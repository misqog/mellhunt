package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.entity.CashierEntity;
import ru.misqog.mellhunt.network.CashierTradePacket;
import ru.misqog.mellhunt.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The casino cashier: the only way to turn gold nuggets into gold ingots.
 *
 * <p>The casino has no crafting table, so the slot machine pays out nuggets
 * that would otherwise be dead weight. She trades nine nuggets for one ingot,
 * keeps the gold and never gives anything back.</p>
 */
@Mod.EventBusSubscriber(modid = ru.misqog.mellhunt.MellstroyMod.MOD_ID)
public final class CasinoCashier {

    /** Where she stands in the casino (world coordinates). */
    public static final double X = 31.5D;
    public static final double Y = 65.0625D;
    public static final double Z = 14.5D;

    /** How far away a player can still talk to her. */
    private static final double REACH_SQR = 5.0D * 5.0D;

    /** Minimum delay between two trades, so a double click cannot dupe. */
    private static final int TRADE_COOLDOWN_TICKS = 3;

    private static final Map<UUID, Long> LAST_TRADE = new HashMap<>();

    /** Right-clicking her opens the exchange instead of anything else. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof CashierEntity)) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            open(player);
        }
    }

    public static void open(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        if (!isNearCashier(player)) {
            if (ru.misqog.mellhunt.world.MellstroyDimensions.isCasino(player.serverLevel())) {
                player.displayClientMessage(
                        Component.translatable("message.mellhunt.cashier.too_far")
                                .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
            return;
        }
        ModNetwork.sendOpenCashier(player);
        player.serverLevel().playSound(null, BlockPos.containing(X, Y, Z),
                SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
    }

    /** Handles one button press from the cashier screen. */
    public static void trade(ServerPlayer player, int action) {
        if (player.isSpectator() || !isNearCashier(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        long last = LAST_TRADE.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now - last < TRADE_COOLDOWN_TICKS) {
            return;
        }
        LAST_TRADE.put(player.getUUID(), now);

        int requested = switch (action) {
            case CashierTradePacket.ALL -> countNuggets(player) / 9;
            case CashierTradePacket.FIVE -> 5;
            case CashierTradePacket.TEN -> 10;
            case CashierTradePacket.TWENTY -> 20;
            default -> 0;
        };
        int available = countNuggets(player) / 9;
        int done = Math.min(requested, available);

        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(X, Y, Z);
        if (done <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.mellhunt.cashier.no_nuggets")
                            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
            return;
        }

        takeNuggets(player, done * 9);
        CasinoMoney.giveIngots(player, done);
        player.inventoryMenu.broadcastChanges();

        level.playSound(null, pos, SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.9F, 1.0F);
        player.displayClientMessage(
                Component.translatable("message.mellhunt.cashier.traded", done)
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }

    /** True when the player stands next to her inside the casino. */
    public static boolean isNearCashier(ServerPlayer player) {
        if (!ru.misqog.mellhunt.world.MellstroyDimensions.isCasino(player.serverLevel())) {
            return false;
        }
        return player.distanceToSqr(X, Y, Z) <= REACH_SQR;
    }

    public static int countNuggets(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.GOLD_NUGGET)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeNuggets(ServerPlayer player, int amount) {
        int left = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.GOLD_NUGGET)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
    }

    /** Drops any pending win too: the player never gets to keep it. */
    public static void forget(UUID id) {
        LAST_TRADE.remove(id);
    }

    private CasinoCashier() {
    }
}
