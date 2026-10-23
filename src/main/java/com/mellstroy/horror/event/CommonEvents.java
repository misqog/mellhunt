package com.mellstroy.horror.event;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.config.MellstroyConfig;
import com.mellstroy.horror.entity.MellstroyEntity;
import com.mellstroy.horror.registry.ModEntities;
import com.mellstroy.horror.registry.ModItems;
import com.mellstroy.horror.spawn.MellstroyRespawnManager;
import com.mellstroy.horror.world.MellstroyHouseManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * Natural spawning (always far away, so there is room to run), the /mellstroy
 * command and a small hint on login.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class CommonEvents {

    private static int spawnTimer = 20 * 30;

    /* ------------------------------------------------------------------ */
    /*  Commands                                                          */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mellstroy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("here").executes(CommonEvents::spawnHere))
                .then(Commands.literal("spawn").executes(CommonEvents::spawnAway))
                .then(Commands.literal("remove").executes(CommonEvents::removeAll)));
    }

    private static int spawnHere(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        float yaw = player.getYRot() * (float) (Math.PI / 180.0D);
        double x = player.getX() - Math.sin(yaw) * 4.0D;
        double z = player.getZ() + Math.cos(yaw) * 4.0D;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));

        MellstroyEntity mob = ModEntities.MELLSTROY.get().create(level);
        if (mob == null) {
            source.sendFailure(Component.translatable("message.mellstroy.no_space"));
            return 0;
        }

        mob.moveTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                MobSpawnType.COMMAND, null, null);
        level.addFreshEntity(mob);
        MellstroyRespawnManager.cancel();

        source.sendSuccess(() -> Component.translatable("message.mellstroy.summoned")
                .withStyle(ChatFormatting.DARK_RED), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnAway(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        int min = MellstroyConfig.spawnMinDistance();
        int max = Math.max(min + 1, MellstroyConfig.spawnMaxDistance());

        if (!trySpawnAway(level, player, min, max)) {
            source.sendFailure(Component.translatable("message.mellstroy.no_space"));
            return 0;
        }
        MellstroyRespawnManager.cancel();

        source.sendSuccess(() -> Component.translatable("message.mellstroy.summoned")
                .withStyle(ChatFormatting.DARK_RED), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<MellstroyEntity> mobs = level.getEntitiesOfClass(MellstroyEntity.class,
                source.getEntity() != null
                        ? source.getEntity().getBoundingBox().inflate(512.0D)
                        : new net.minecraft.world.phys.AABB(-30000000, -1000, -30000000, 30000000, 4000, 30000000));

        int count = 0;
        for (MellstroyEntity mob : mobs) {
            mob.discard();
            count++;
        }
        MellstroyRespawnManager.cancel();

        if (count == 0) {
            source.sendFailure(Component.translatable("message.mellstroy.already_gone"));
            return 0;
        }

        final int removed = count;
        source.sendSuccess(() -> Component.translatable("message.mellstroy.removed", removed)
                .withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ------------------------------------------------------------------ */
    /*  Natural spawning                                                  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        // The special respawn after the casino runs even if natural spawning
        // is disabled. While it is pending, natural spawning is suppressed.
        if (MellstroyRespawnManager.tick(server)) {
            return;
        }
        if (!MellstroyConfig.naturalSpawn()) {
            return;
        }
        if (MellstroyRespawnManager.isPending() || MellstroyHouseManager.isRushActive()) {
            return;
        }

        if (spawnTimer > 0) {
            spawnTimer--;
            return;
        }
        spawnTimer = MellstroyConfig.spawnIntervalSeconds() * 20;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }

        ServerPlayer player = players.get(server.overworld().random.nextInt(players.size()));
        ServerLevel level = player.serverLevel();

        if (MellstroyConfig.spawnOnlyAtNight() && !level.isNight()) {
            return;
        }

        // Never more than one of him per player, and only when he is far away.
        if (!level.getEntitiesOfClass(MellstroyEntity.class, player.getBoundingBox().inflate(200.0D)).isEmpty()) {
            return;
        }

        if (level.random.nextFloat() > 0.6F) {
            return;
        }

        int min = MellstroyConfig.spawnMinDistance();
        int max = Math.max(min + 1, MellstroyConfig.spawnMaxDistance());
        trySpawnAway(level, player, min, max);
    }

    /**
     * Tries to spawn Mellstroy on the surface between {@code min} and
     * {@code max} blocks away from the player. Never closer than the minimum,
     * so the player always has room to run.
     */
    private static boolean trySpawnAway(ServerLevel level, ServerPlayer player, int min, int max) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = min + level.random.nextDouble() * (max - min);

            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);

            if (player.distanceToSqr(x + 0.5D, player.getY(), z + 0.5D) < (double) min * min) {
                continue;
            }

            BlockPos surface = new BlockPos(x, level.getMinBuildHeight(), z);
            if (!level.isLoaded(surface)) {
                continue;
            }

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();

            if (y <= level.getMinBuildHeight() + 2) {
                continue;
            }

            BlockState ground = level.getBlockState(below);
            if (!ground.isSolidRender(level, below)) {
                continue;
            }
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            if (!level.getFluidState(pos).isEmpty()) {
                continue;
            }

            MellstroyEntity mob = ModEntities.MELLSTROY.get().create(level);
            if (mob == null) {
                return false;
            }

            mob.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(mob);

            player.displayClientMessage(
                    Component.translatable("message.mellstroy.arrived")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            return true;
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /*  One Mellstroy in the whole world                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Every Mellstroy that enters a level goes through here. A newly spawned
     * one deletes all previous copies, and an old copy loaded from a chunk is
     * dropped as soon as a newer one is active.
     */
    @SubscribeEvent
    public static void onMellstroyJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof MellstroyEntity mob)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (MellstroyEntity.handleJoin(level, mob, event.loadedFromDisk())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MellstroyEntity.resetActive();
    }

    /* ------------------------------------------------------------------ */
    /*  Misc                                                              */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.MELLSTROY_SPAWN_EGG);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.mellstroy.hint")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
    }

    private CommonEvents() {
    }
}
