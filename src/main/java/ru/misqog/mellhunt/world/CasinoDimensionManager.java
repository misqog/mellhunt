package ru.misqog.mellhunt.world;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.block.CasinoChairBlock;
import ru.misqog.mellhunt.casino.CasinoManager;
import ru.misqog.mellhunt.casino.CasinoMoney;
import ru.misqog.mellhunt.casino.ActiveBets;
import ru.misqog.mellhunt.casino.PendingPayouts;
import ru.misqog.mellhunt.entity.CashierEntity;
import ru.misqog.mellhunt.entity.ChairSeatEntity;
import ru.misqog.mellhunt.entity.MellstroyEntity;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.registry.ModEntities;
import ru.misqog.mellhunt.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hidden casino dimension.
 *
 * <p>The only entrance is being completely buried alive: when both the block
 * at the player's feet and the block at their head suffocate them for a short
 * moment, they are pulled into the casino. Their whole inventory is stored in
 * the Overworld and replaced with ten gold ingots. Collecting thirty-six gold
 * ingots sends them back exactly where they came from, with the original
 * inventory restored and the winnings merged in.</p>
 *
 * <p>The casino itself is a fixed structure placed once into a void flat
 * world. A single passive, seated Mellstroy watches the laptop room.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class CasinoDimensionManager {

    private static final String DATA_NAME = "mellhunt_casino";

    /** Size of casino_mell.nbt. */
    private static final int STRUCTURE_WIDTH = 37;
    private static final int STRUCTURE_HEIGHT = 7;
    private static final int STRUCTURE_DEPTH = 27;

    /** Where the structure is placed in the casino dimension. */
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    /**
     * A block that is always solid in the finished casino. It is air in the
     * empty void world, which makes it a cheap "is the structure here?" test.
     */
    private static final BlockPos SIGNATURE_LOCAL = new BlockPos(16, 0, 7);

    /** Player spawn on the upper-west balcony: block (3, 67, 15). */
    private static final double SPAWN_X = 3.5D;
    private static final double SPAWN_Y = 67.0625D;
    private static final double SPAWN_Z = 15.5D;
    /** Facing east, into the casino. */
    private static final float SPAWN_YAW = 270.0F;

    /** The two laptop chairs; he is placed on the first one that is free. */
    private static final BlockPos CHAIR_LOCAL = new BlockPos(24, 2, 3);
    private static final BlockPos CHAIR_ALT_LOCAL = new BlockPos(30, 2, 3);

    /** Where the cashier stands: world (31, 65, 14), local (31, 1, 14). */
    private static final double CASHIER_X = 31.5D;
    private static final double CASHIER_Y = 65.0625D;
    private static final double CASHIER_Z = 14.5D;
    /** She looks south, exactly like the reference screenshot. */
    private static final float CASHIER_YAW = 41.0F;

    /** How long the player has to reach 36 ingots before the gold runs out. */
    private static final int LOCKED_TICKS = 20 * 5;
    /**
     * Grace period after an empty inventory before the countdown starts. The
     * round result (reels, cards, roulette ball) takes a few seconds, and the
     * player must see it before the death timer begins.
     */
    private static final int RESULT_GRACE_TICKS = 20 * 6;
    /** Only allow one gold trade per tick, so an impatient click cannot dupe. */
    private static final int TRADE_COOLDOWN_TICKS = 4;

    private static final int STARTING_INGOTS = 10;
    private static final int GOAL_INGOTS = 36;

    /** How many consecutive ticks the player must be fully buried. */
    private static final int BURIED_TICKS = 6;
    /** Blocks immediate re-entry after leaving the casino. */
    private static final int REENTRY_COOLDOWN_TICKS = 200;
    /** How long to wait before sending the entry track after the teleport. */
    private static final int ENTRY_DELAY_TICKS = 10;

    private static final Map<UUID, Integer> BURIED = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    /**
     * The entry track is sent a couple of ticks after the teleport: the client
     * is still rebuilding its world right after a dimension change and would
     * otherwise drop the sound and the title.
     */
    private static final Map<UUID, Integer> PENDING_ENTRY = new HashMap<>();
    /** Ticks left until a player who lost every ingot inside the casino dies. */
    private static final Map<UUID, LockdownState> LOCKDOWN = new HashMap<>();

    /** Progress of the "you are broke" countdown. */
    private static final class LockdownState {
        private int ticks = LOCKED_TICKS;
        private int shownSeconds = -1;
        private boolean announced;
        /** The player died and is waiting for the respawn screen. */
        private boolean dying;
        /** Ticks left before the countdown itself may start. */
        private int grace = RESULT_GRACE_TICKS;
    }

    private CasinoDimensionManager() {
    }

    /* ------------------------------------------------------------------ */
    /*  Lifecycle                                                         */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ensureCasino(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BURIED.clear();
        COOLDOWN.clear();
        PENDING_ENTRY.clear();
        LOCKDOWN.clear();
    }

    /* ------------------------------------------------------------------ */
    /*  Entry / exit                                                      */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        COOLDOWN.replaceAll((id, ticks) -> ticks - 1);
        COOLDOWN.entrySet().removeIf(entry -> entry.getValue() <= 0);
        tickPendingEntries(server);

        for (ServerLevel level : server.getAllLevels()) {
            boolean casino = MellstroyDimensions.isCasino(level);
            if (casino && !level.players().isEmpty() && level.getGameTime() % 20L == 0L) {
                // Entities are not guaranteed to be loaded yet when the server
                // starts, so the decorative Mellstroy is reconciled only while
                // the casino actually has a visitor.
                ensureSeatedMellstroy(level);
            }
            // Teleporting a player changes the level's player list, so iterate
            // over a snapshot instead of the live list.
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (casino) {
                    tickCasinoPlayer(server, player);
                } else {
                    tickOverworldPlayer(server, player);
                }
            }
        }
    }

    /** Sends the queued entry track and title once the client has the new world. */
    private static void tickPendingEntries(MinecraftServer server) {
        if (PENDING_ENTRY.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_ENTRY.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> pending = iterator.next();
            int ticks = pending.getValue() - 1;
            if (ticks > 0) {
                pending.setValue(ticks);
                continue;
            }
            iterator.remove();

            ServerPlayer player = server.getPlayerList().getPlayer(pending.getKey());
            if (player == null || !MellstroyDimensions.isCasino(player.serverLevel())) {
                continue;
            }
            ModNetwork.sendPlayerSound(player, ModSounds.POSHLA_VOZNYA.get(), 1.0F, 1.0F);
            ModNetwork.sendCasinoEntry(player, Math.round(ModSounds.POSHLA_VOZNYA_SECONDS * 20.0F));
            ModNetwork.sendCashierHint(player, true);
        }
    }

    /** Watches for the "buried alive" trigger outside the casino. */
    private static void tickOverworldPlayer(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        if (player.isSpectator() || player.isCreative() || player.isPassenger()
                || player.isDeadOrDying()
                || CasinoManager.isInCasino(player)
                || data(server).hasSnapshot(id)
                || COOLDOWN.containsKey(id)) {
            BURIED.remove(id);
            return;
        }

        BlockPos feet = player.blockPosition();
        if (!isBuried(player, feet)) {
            BURIED.remove(id);
            return;
        }

        int ticks = BURIED.getOrDefault(id, 0) + 1;
        if (ticks < BURIED_TICKS) {
            BURIED.put(id, ticks);
            return;
        }

        BURIED.remove(id);
        enterCasino(server, player);
    }

    /**
     * Both the feet block and the head block must be solid, suffocating blocks.
     * A single layer of sand or two gravel blocks are enough.
     */
    private static boolean isBuried(ServerPlayer player, BlockPos feet) {
        BlockPos head = feet.above();
        BlockState feetState = player.level().getBlockState(feet);
        BlockState headState = player.level().getBlockState(head);
        return feetState.isSuffocating(player.level(), feet)
                && headState.isSuffocating(player.level(), head);
    }

    private static void enterCasino(MinecraftServer server, ServerPlayer player) {
        ServerLevel casino = server.getLevel(MellstroyDimensions.CASINO);
        if (casino == null) {
            MellstroyMod.LOGGER.warn("[Mellstroy] The casino dimension is missing; entry skipped.");
            return;
        }
        if (!ensureCasino(server)) {
            MellstroyMod.LOGGER.warn("[Mellstroy] The casino structure is not ready; entry skipped.");
            return;
        }

        CasinoData data = data(server);
        if (data.hasSnapshot(player.getUUID())) {
            return;
        }
        data.snapshots.put(player.getUUID(), Snapshot.capture(player));
        data.setDirty();

        player.stopRiding();
        player.getInventory().clearContent();
        CasinoMoney.giveIngots(player, STARTING_INGOTS);

        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);

        player.teleportTo(casino, SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_YAW, 0.0F);
        // The dimension change rebuilds the client player; resync the new
        // inventory (the ten starting ingots) right after it.
        player.inventoryMenu.broadcastChanges();

        LOCKDOWN.remove(player.getUUID());
        PENDING_ENTRY.put(player.getUUID(), ENTRY_DELAY_TICKS);
        sendEntryInstructions(player);
    }

    private static void sendEntryInstructions(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.mellhunt.casino.entry")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("message.mellhunt.casino.goal")
                .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable("message.mellhunt.casino.nuggets")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("message.mellhunt.casino.play")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void tickCasinoPlayer(MinecraftServer server, ServerPlayer player) {
        CasinoData data = data(server);
        Snapshot snapshot = data.snapshots.get(player.getUUID());
        if (snapshot == null) {
            // Somebody used a command to get here. Never leave them stranded in
            // a void world they cannot leave on their own.
            LOCKDOWN.remove(player.getUUID());
            returnToOverworldSpawn(server, player);
            return;
        }

        // Any gold in hand (a win, a trade, leftovers) cancels the countdown.
        if (countAllGold(player) >= GOAL_INGOTS) {
            exitCasino(server, player, snapshot, true);
            return;
        }
        if (countAllGold(player) > 0) {
            if (LOCKDOWN.remove(player.getUUID()) != null) {
                ModNetwork.sendCasinoCountdown(player, 0);
            }
            return;
        }

        if (player.isDeadOrDying()) {
            return;
        }

        tickLockdown(server, player);
    }

    /**
     * Losing the starting gold is fatal: a countdown is shown in the middle of
     * the screen, and after five seconds the player dies. Death is the way out,
     * and the inventory from before the casino is restored on return.
     */
    private static void tickLockdown(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        LockdownState state = LOCKDOWN.computeIfAbsent(id, key -> new LockdownState());
        if (state.dying) {
            return;
        }

        // Wait for the round result: reels, cards and the wheel all take a few
        // seconds, and the countdown must only start once they are on screen.
        if (ActiveBets.hasBet(id) || PendingPayouts.hasPending(id)) {
            return;
        }
        if (state.grace > 0) {
            state.grace--;
            return;
        }

        if (!state.announced) {
            state.announced = true;
            player.sendSystemMessage(Component.translatable("message.mellhunt.casino.broke")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }

        if (state.ticks <= 0) {
            // Kill exactly once and end the session here, so the respawn handler
            // has nothing left to do and cannot start a second countdown.
            state.dying = true;
            LOCKDOWN.remove(id);
            ModNetwork.sendCasinoCountdown(player, 0);
            player.kill();
            return;
        }

        state.ticks--;
        int seconds = Math.max(0, (state.ticks + 19) / 20);
        if (seconds != state.shownSeconds) {
            state.shownSeconds = seconds;
            ModNetwork.sendCasinoCountdown(player, Math.max(1, seconds));
        }
    }

    /**
     * Gold in the inventory plus anything on the cursor or in the 2x2 crafting
     * grid. Ingots that were just crafted must count immediately, otherwise the
     * player would have to shuffle them into a slot first.
     */
    private static int countAllGold(ServerPlayer player) {
        int count = CasinoMoney.countGold(player);
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.is(Items.GOLD_INGOT)) {
            count += carried.getCount();
        }
        CraftingContainer craft = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < craft.getContainerSize(); i++) {
            ItemStack stack = craft.getItem(i);
            if (stack.is(Items.GOLD_INGOT)) {
                count += stack.getCount();
            }
        }
        // The offhand is not part of the main container.
        for (int slot = 0; slot < player.getInventory().offhand.size(); slot++) {
            ItemStack stack = player.getInventory().offhand.get(slot);
            if (stack.is(Items.GOLD_INGOT)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void exitCasino(MinecraftServer server, ServerPlayer player, Snapshot snapshot,
                                   boolean keepCasinoItems) {        CasinoData data = data(server);
        data.snapshots.remove(player.getUUID());
        data.setDirty();
        PENDING_ENTRY.remove(player.getUUID());
        LOCKDOWN.remove(player.getUUID());
        ModNetwork.sendCasinoCountdown(player, 0);

        ServerLevel target = resolveLevel(server, snapshot.dimension);
        Vec3 safe = safePosition(player, target, snapshot.x, snapshot.y, snapshot.z);
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        teleportSafely(player, target, safe, snapshot.yRot, snapshot.xRot);
        restoreInventory(player, snapshot, keepCasinoItems);
        COOLDOWN.put(player.getUUID(), REENTRY_COOLDOWN_TICKS);
    }

    private static void returnToOverworldSpawn(MinecraftServer server, ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        Vec3 safe = safePosition(player, overworld, spawn.getX() + 0.5D,
                spawn.getY(), spawn.getZ() + 0.5D);
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        teleportSafely(player, overworld, safe, 0.0F, 0.0F);
        ModNetwork.sendCasinoCountdown(player, 0);
        COOLDOWN.put(player.getUUID(), REENTRY_COOLDOWN_TICKS);
    }

    /* ------------------------------------------------------------------ */
    /*  Inventory safety                                                  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // A snapshot only survives a login when the server stopped while the
        // player was inside the casino (crash, kill -9, power loss). Put them
        // back on solid ground and give their real inventory back.
        restoreAndReturn(server, player, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // Quitting is not an escape with a reward: the original inventory is
        // restored, but the casino's ten ingots and any winnings are dropped.
        restoreAndReturn(server, player, false);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        restoreAndReturn(server, player, false);
    }

    private static void restoreAndReturn(MinecraftServer server, ServerPlayer player,
                                         boolean keepCasinoItems) {
        CasinoData data = data(server);
        Snapshot snapshot = data.snapshots.remove(player.getUUID());
        if (snapshot == null) {
            return;
        }
        data.setDirty();
        PENDING_ENTRY.remove(player.getUUID());
        LOCKDOWN.remove(player.getUUID());
        PendingPayouts.discard(player.getUUID());
        ActiveBets.clear(player.getUUID());
        ModNetwork.sendCasinoCountdown(player, 0);

        ServerLevel target = resolveLevel(server, snapshot.dimension);
        Vec3 safe = safePosition(player, target, snapshot.x, snapshot.y, snapshot.z);
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        teleportSafely(player, target, safe, snapshot.yRot, snapshot.xRot);
        // Drop whatever survived the casino (gold, winnings) before the real
        // inventory goes back; death must never keep the casino loot.
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        if (player.inventoryMenu.getCraftSlots().getContainerSize() > 0) {
            for (int i = 0; i < player.inventoryMenu.getCraftSlots().getContainerSize(); i++) {
                player.inventoryMenu.getCraftSlots().setItem(i, ItemStack.EMPTY);
            }
        }
        restoreInventory(player, snapshot, keepCasinoItems);
        COOLDOWN.put(player.getUUID(), REENTRY_COOLDOWN_TICKS);
    }

    /**
     * A dimension change during logout must never take the whole save down with
     * it. If it fails, the player stays where they are; the casino tick handler
     * and the login handler both retry the return later.
     */
    private static void teleportSafely(ServerPlayer player, ServerLevel target, Vec3 pos,
                                       float yRot, float xRot) {
        try {
            player.teleportTo(target, pos.x, pos.y, pos.z, yRot, xRot);
        } catch (RuntimeException exception) {
            MellstroyMod.LOGGER.warn("[Mellstroy] Could not move {} out of the casino: {}",
                    player.getGameProfile().getName(), exception.toString());
        }
    }

    /**
     * Puts the original inventory back. When the player actually won, the
     * casino inventory (the starting ten ingots, the thirty-six that opened
     * the exit and anything left over) is merged into free slots; overflow is
     * dropped at the player's feet.
     */
    private static void restoreInventory(ServerPlayer player, Snapshot snapshot,
                                         boolean keepCasinoItems) {
        Inventory inventory = player.getInventory();
        List<ItemStack> casinoItems = new ArrayList<>();
        if (keepCasinoItems) {
            // The cursor stack and the crafting grid are not part of
            // Inventory. Take them along too so gold that happened to be held
            // there is not silently destroyed on the way out.
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                casinoItems.add(carried.copy());
            }
            CraftingContainer craft = player.inventoryMenu.getCraftSlots();
            for (int i = 0; i < craft.getContainerSize(); i++) {
                ItemStack stack = craft.getItem(i);
                if (!stack.isEmpty()) {
                    casinoItems.add(stack.copy());
                }
            }
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    casinoItems.add(stack.copy());
                }
            }
            // Offhand is a separate list and would otherwise be lost.
            for (int slot = 0; slot < inventory.offhand.size(); slot++) {
                ItemStack stack = inventory.offhand.get(slot);
                if (!stack.isEmpty()) {
                    casinoItems.add(stack.copy());
                }
            }
        }

        inventory.clearContent();
        inventory.load(snapshot.inventory.copy());
        inventory.setChanged();

        // The crafting grid and cursor stack are not part of Inventory, so
        // they were taken out separately when the player entered.
        for (int i = 0; i < snapshot.extras.size(); i++) {
            ItemStack stack = ItemStack.of(snapshot.extras.getCompound(i));
            if (!stack.isEmpty() && !inventory.add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        clearTransient(player);

        for (ItemStack stack : casinoItems) {
            if (!inventory.add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Takes out the 2x2 crafting grid and the item on the cursor, which are
     * not part of {@link Inventory}. Without this a player could carry a full
     * stack into the casino through the crafting screen.
     */
    private static ListTag captureExtras(ServerPlayer player) {
        ListTag extras = new ListTag();

        // A chest or another menu may be open while the player gets buried.
        // Keep its cursor stack, then close the menu before touching anything.
        AbstractContainerMenu openMenu = player.containerMenu;
        ItemStack carried = openMenu.getCarried();
        if (!carried.isEmpty()) {
            extras.add(carried.copy().save(new CompoundTag()));
            openMenu.setCarried(ItemStack.EMPTY);
        }
        if (openMenu != player.inventoryMenu) {
            player.doCloseContainer();
        }

        CraftingContainer craft = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < craft.getContainerSize(); i++) {
            ItemStack stack = craft.getItem(i);
            if (!stack.isEmpty()) {
                extras.add(stack.copy().save(new CompoundTag()));
                craft.setItem(i, ItemStack.EMPTY);
            }
        }
        player.inventoryMenu.broadcastChanges();
        return extras;
    }

    /** Empties the cursor stack and the player's crafting grid. */
    private static void clearTransient(ServerPlayer player) {
        player.containerMenu.setCarried(ItemStack.EMPTY);
        CraftingContainer craft = player.inventoryMenu.getCraftSlots();
        for (int i = 0; i < craft.getContainerSize(); i++) {
            craft.setItem(i, ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * The stored position is inside the block the player was buried in, so it
     * is not safe to return to directly. Look for a free space next to it
     * first (a spot the player can simply stand on), then above it, and only
     * as a last resort keep the original spot.
     */
    private static Vec3 safePosition(ServerPlayer player, ServerLevel level,
                                     double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);

        // Spiral outwards, nearest ring first: the player lands next to the
        // burial instead of inside it.
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    double px = base.getX() + dx + 0.5D;
                    double pz = base.getZ() + dz + 0.5D;
                    for (int dy = 0; dy <= 3; dy++) {
                        double py = base.getY() + dy;
                        if (isStandable(level, player, px, py, pz)) {
                            return new Vec3(px, py, pz);
                        }
                    }
                }
            }
        }

        // Nothing beside it: use the first free space above the burial.
        for (int dy = 1; dy <= 8; dy++) {
            double py = base.getY() + dy;
            if (isStandable(level, player, x, py, z)) {
                return new Vec3(x, py, z);
            }
        }
        return new Vec3(x, y, z);
    }

    /** The player's bounding box fits (the block below is not required). */
    private static boolean isStandable(ServerLevel level, ServerPlayer player,
                                       double x, double y, double z) {
        AABB box = new AABB(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D);
        return level.noCollision(player, box);
    }

    private static ServerLevel resolveLevel(MinecraftServer server, ResourceLocation dimension) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        if (MellstroyDimensions.CASINO.equals(key)) {
            return server.overworld();
        }
        ServerLevel level = server.getLevel(key);
        return level == null ? server.overworld() : level;
    }

    /* ------------------------------------------------------------------ */
    /*  Structure and the seated Mellstroy                                */
    /* ------------------------------------------------------------------ */

    /**
     * Loads the structure chunks and places the casino once.
     *
     * @return true when the structure is present and the dimension is safe to
     *         enter.
     */
    private static boolean ensureCasino(MinecraftServer server) {
        ServerLevel casino = server.getLevel(MellstroyDimensions.CASINO);
        if (casino == null) {
            return false;
        }

        loadStructureChunks(casino);

        BlockPos signature = ORIGIN.offset(SIGNATURE_LOCAL);
        boolean present = !casino.getBlockState(signature).isAir();
        if (!present) {
            // Never rebuild the casino under somebody's feet. The structure is
            // placed on server start, so this only matters for admins who
            // deliberately emptied the dimension while it was occupied.
            if (!casino.players().isEmpty()) {
                return false;
            }
            placeStructure(casino);
            present = !casino.getBlockState(signature).isAir();
        }

        if (!present) {
            return false;
        }
        removeOldCraftingTables(casino);
        ensureCashier(casino);
        return true;
    }

    /**
     * Older builds dropped a crafting table near the entrance. Nuggets are
     * traded at the cashier now, so any leftover table is removed.
     */
    private static void removeOldCraftingTables(ServerLevel level) {
        BlockPos min = ORIGIN;
        BlockPos max = ORIGIN.offset(STRUCTURE_WIDTH - 1, STRUCTURE_HEIGHT - 1, STRUCTURE_DEPTH - 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                level.removeBlock(pos, false);
            }
        }
    }

    /**
     * The casino is built from a fixed structure. Its cashier is the one who
     * swaps nuggets for ingots, so she is placed here if she is missing.
     */
    private static void ensureCashier(ServerLevel level) {
        List<CashierEntity> nearby = level.getEntitiesOfClass(CashierEntity.class,
                new AABB(CASHIER_X - 4.0D, CASHIER_Y - 2.0D, CASHIER_Z - 4.0D,
                        CASHIER_X + 4.0D, CASHIER_Y + 3.0D, CASHIER_Z + 4.0D));
        CashierEntity cashier = nearby.isEmpty() ? null : nearby.get(0);
        for (int i = 1; i < nearby.size(); i++) {
            nearby.get(i).discard();
        }

        if (cashier == null) {
            cashier = ModEntities.CASHIER.get().create(level);
            if (cashier == null) {
                return;
            }
            cashier.moveTo(CASHIER_X, CASHIER_Y, CASHIER_Z, CASHIER_YAW, 0.0F);
            level.addFreshEntity(cashier);
        }

        cashier.setNoAi(true);
        cashier.setInvulnerable(true);
        cashier.setPersistenceRequired();
        cashier.setCustomName(Component.translatable("entity.mellhunt.cashier"));
        cashier.setCustomNameVisible(true);
        cashier.setPos(CASHIER_X, CASHIER_Y, CASHIER_Z);
        cashier.setYRot(CASHIER_YAW);
        cashier.setYHeadRot(CASHIER_YAW);
        cashier.setYBodyRot(CASHIER_YAW);
    }

    private static void loadStructureChunks(ServerLevel level) {
        int minChunkX = ORIGIN.getX() >> 4;
        int minChunkZ = ORIGIN.getZ() >> 4;
        int maxChunkX = (ORIGIN.getX() + STRUCTURE_WIDTH - 1) >> 4;
        int maxChunkZ = (ORIGIN.getZ() + STRUCTURE_DEPTH - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static boolean placeStructure(ServerLevel level) {
        StructureTemplate template = level.getServer().getStructureManager()
                .get(new ResourceLocation(MellstroyMod.MOD_ID, "casino_mell"))
                .orElse(null);
        if (template == null) {
            MellstroyMod.LOGGER.warn("[Mellstroy] casino_mell.nbt is missing; the casino stays empty.");
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        if (!template.placeInWorld(level, ORIGIN, ORIGIN, settings, level.random, 2)) {
            MellstroyMod.LOGGER.warn("[Mellstroy] Could not place the casino structure.");
            return false;
        }
        return true;
    }

    /**
     * Keeps exactly one decorative Mellstroy sitting on one of the two laptop
     * chairs. If neither chair is there (or both are taken by players), nothing
     * is spawned.
     */
    private static void ensureSeatedMellstroy(ServerLevel level) {
        // Entities only exist once their chunk is loaded. Without this the
        // lookup below would return nothing on the first call and happily
        // create a second copy on top of the saved one.
        loadStructureChunks(level);

        AABB bounds = new AABB(
                ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ(),
                ORIGIN.getX() + STRUCTURE_WIDTH,
                ORIGIN.getY() + STRUCTURE_HEIGHT,
                ORIGIN.getZ() + STRUCTURE_DEPTH);
        List<MellstroyEntity> existing = level.getEntitiesOfClass(MellstroyEntity.class, bounds);

        // Prefer the chair he already sits on so he never hops around.
        BlockPos chairPos = null;
        for (MellstroyEntity mob : existing) {
            if (mob.getVehicle() instanceof ChairSeatEntity seat
                    && isLaptopChair(level, seat.getSeatPos())) {
                chairPos = seat.getSeatPos();
                break;
            }
        }
        if (chairPos == null) {
            chairPos = findFreeChair(level);
        }
        if (chairPos == null) {
            return;
        }

        BlockState chairState = level.getBlockState(chairPos);
        Direction facing = chairState.getValue(CasinoChairBlock.FACING);
        // The laptop always stands on the side the chair faces, so he has to
        // look exactly that way to have his face in front of the screen.
        float laptopYaw = facing.toYRot();
        ChairSeatEntity seat = findSeat(level, chairPos);
        discardDuplicateSeats(level, chairPos, seat);

        MellstroyEntity mellstroy = null;
        for (MellstroyEntity mob : existing) {
            if (mob.getVehicle() instanceof ChairSeatEntity mobSeat
                    && chairPos.equals(mobSeat.getSeatPos())) {
                mellstroy = mob;
                break;
            }
        }
        if (mellstroy == null && !existing.isEmpty()) {
            // Nothing is seated on the chosen chair yet: reuse the first copy
            // and re-seat it, instead of growing another one.
            mellstroy = existing.get(0);
        }
        // The casino must never accumulate copies.
        for (MellstroyEntity mob : existing) {
            if (mob != mellstroy) {
                mob.stopRiding();
                mob.discard();
            }
        }

        if (mellstroy == null) {
            mellstroy = ModEntities.MELLSTROY.get().create(level);
            if (mellstroy == null) {
                return;
            }
            mellstroy.moveTo(chairPos.getX() + 0.5D, chairPos.getY() + 0.625D,
                    chairPos.getZ() + 0.5D, laptopYaw, 0.0F);
            mellstroy.finalizeSpawn(level, level.getCurrentDifficultyAt(chairPos),
                    MobSpawnType.STRUCTURE, null, null);
            level.addFreshEntity(mellstroy);
        }

        // Whatever was already there, he is always harmless and permanent.
        mellstroy.setNoAi(true);
        mellstroy.setInvulnerable(true);
        mellstroy.setPersistenceRequired();

        if (seat == null) {
            seat = ModEntities.CHAIR_SEAT.get().create(level);
            if (seat == null) {
                return;
            }
            seat.setSeatPos(chairPos);
            seat.setYRot(laptopYaw);
            level.addFreshEntity(seat);
        } else if (seat.getSeatPos() == null) {
            seat.setSeatPos(chairPos);
        }
        if (mellstroy.getVehicle() != seat) {
            mellstroy.stopRiding();
            mellstroy.moveTo(chairPos.getX() + 0.5D, chairPos.getY() + 0.625D,
                    chairPos.getZ() + 0.5D, laptopYaw, 0.0F);
            mellstroy.startRiding(seat, true);
        }
        // Riding keeps the entity yaw but renders with the seat's rotation, so
        // both the seat and the body are turned towards the laptop.
        if (seat.getYRot() != laptopYaw) {
            seat.setYRot(laptopYaw);
            seat.setYHeadRot(laptopYaw);
        }
        mellstroy.setYRot(laptopYaw);
        mellstroy.setYHeadRot(laptopYaw);
        mellstroy.setYBodyRot(laptopYaw);
    }

    /** The first laptop chair that no player is using, or null. */
    private static BlockPos findFreeChair(ServerLevel level) {
        for (BlockPos local : new BlockPos[]{CHAIR_LOCAL, CHAIR_ALT_LOCAL}) {
            if (!isLaptopChair(level, ORIGIN.offset(local))) {
                continue;
            }
            ChairSeatEntity seat = findSeat(level, ORIGIN.offset(local));
            if (seat != null && !seat.getPassengers().isEmpty()
                    && seat.getPassengers().stream()
                            .noneMatch(passenger -> passenger instanceof MellstroyEntity)) {
                continue;
            }
            return ORIGIN.offset(local);
        }
        return null;
    }

    private static boolean isLaptopChair(ServerLevel level, BlockPos pos) {
        return pos != null && level.getBlockState(pos).getBlock() instanceof CasinoChairBlock;
    }

    /**
     * Empty seats remove themselves after a few ticks, but a seat that is
     * loaded together with its passenger can pile up on repeated server starts.
     * Keep exactly the seat Mellstroy is about to use.
     */
    private static void discardDuplicateSeats(ServerLevel level, BlockPos chairPos,
                                              ChairSeatEntity keep) {
        AABB seatBounds = new AABB(
                chairPos.getX(), chairPos.getY(), chairPos.getZ(),
                chairPos.getX() + 1.0D, chairPos.getY() + 1.5D, chairPos.getZ() + 1.0D);
        for (ChairSeatEntity seat : level.getEntitiesOfClass(ChairSeatEntity.class, seatBounds)) {
            if (seat == keep) {
                continue;
            }
            BlockPos seatPos = seat.getSeatPos();
            if (seatPos != null && !seatPos.equals(chairPos)) {
                continue;
            }
            if (!seat.getPassengers().isEmpty()
                    && seat.getPassengers().stream()
                            .noneMatch(passenger -> passenger instanceof MellstroyEntity)) {
                continue;
            }
            seat.ejectPassengers();
            seat.discard();
        }
    }

    private static ChairSeatEntity findSeat(ServerLevel level, BlockPos chairPos) {
        AABB seatBounds = new AABB(
                chairPos.getX(), chairPos.getY(), chairPos.getZ(),
                chairPos.getX() + 1.0D, chairPos.getY() + 1.5D, chairPos.getZ() + 1.0D);
        List<ChairSeatEntity> seats = level.getEntitiesOfClass(ChairSeatEntity.class, seatBounds);
        for (ChairSeatEntity seat : seats) {
            BlockPos seatPos = seat.getSeatPos();
            if (seatPos == null || seatPos.equals(chairPos)) {
                return seat;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /*  Saved data                                                        */
    /* ------------------------------------------------------------------ */

    private static CasinoData data(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CasinoData::load, CasinoData::new, DATA_NAME);
    }

    /** The Overworld-held copy of every player's real inventory. */
    private static final class CasinoData extends SavedData {
        private final Map<UUID, Snapshot> snapshots = new HashMap<>();

        private boolean hasSnapshot(UUID id) {
            return snapshots.containsKey(id);
        }

        private static CasinoData load(CompoundTag tag) {
            CasinoData data = new CasinoData();
            ListTag list = tag.getList("Snapshots", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.hasUUID("Player")) {
                    continue;
                }
                data.snapshots.put(entry.getUUID("Player"), Snapshot.load(entry));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Snapshot> entry : snapshots.entrySet()) {
                CompoundTag snapshot = entry.getValue().save();
                snapshot.putUUID("Player", entry.getKey());
                list.add(snapshot);
            }
            tag.put("Snapshots", list);
            return tag;
        }
    }

    /** Everything needed to put one player back exactly where they were. */
    private static final class Snapshot {
        private final ResourceLocation dimension;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float xRot;
        private final ListTag inventory;
        /** The crafting grid and cursor stack, which Inventory does not hold. */
        private final ListTag extras;

        private Snapshot(ResourceLocation dimension, double x, double y, double z,
                         float yRot, float xRot, ListTag inventory, ListTag extras) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.inventory = inventory;
            this.extras = extras;
        }

        private static Snapshot capture(ServerPlayer player) {
            ListTag extras = captureExtras(player);
            ListTag inventory = new ListTag();
            player.getInventory().save(inventory);
            return new Snapshot(player.serverLevel().dimension().location(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), inventory, extras);
        }

        private static Snapshot load(CompoundTag tag) {
            return new Snapshot(
                    new ResourceLocation(tag.getString("Dimension")),
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z"),
                    tag.getFloat("Yaw"),
                    tag.getFloat("Pitch"),
                    tag.getList("Inventory", Tag.TAG_COMPOUND),
                    tag.getList("Extras", Tag.TAG_COMPOUND));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension.toString());
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("Yaw", yRot);
            tag.putFloat("Pitch", xRot);
            tag.put("Inventory", inventory.copy());
            tag.put("Extras", extras.copy());
            return tag;
        }
    }
}
