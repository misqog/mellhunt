package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.block.LaptopBlock;
import ru.misqog.mellhunt.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The playable slot game on the MSI laptop.
 *
 * <p>Normal right-click opens the screen; shift + right-click rotates the
 * laptop. A spin costs one gold ingot per active line. The prize is paid only
 * after the client has finished the reel animation.</p>
 */
public final class LaptopSlotManager {

    private static final int MAX_DISTANCE_SQR = 64;

    public static void open(ServerPlayer player, BlockPos pos) {
        if (!isValid(player, pos)) {
            return;
        }
        ModNetwork.sendOpenLaptopSlots(player, pos);
    }

    public static void spin(ServerPlayer player, BlockPos pos, int lines, boolean turbo) {
        if (!isValid(player, pos)) {
            return;
        }
        lines = Math.max(1, Math.min(LaptopSlots.LINES.length, lines));

        boolean free = player.isCreative();
        if (!free && !CasinoMoney.takeGold(player, lines)) {
            ModNetwork.sendLaptopResult(player, new int[LaptopSlots.CELLS], 0, 0, 1);
            player.displayClientMessage(
                    Component.translatable("laptop.slots.no_gold"), true);
            return;
        }

        int[] grid = LaptopSlots.randomGrid(player.getRandom());
        int[] mask = new int[1];
        int payout = LaptopSlots.evaluate(grid, mask, lines);

        if (payout > 0) {
            int delay = turbo ? 55 : 100;
            PendingPayouts.schedule(player,
                    new ItemStack(Items.GOLD_INGOT, payout / 9), delay);
            PendingPayouts.schedule(player,
                    new ItemStack(Items.GOLD_NUGGET, payout % 9), delay);
        }

        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.55F, 1.25F);
        ModNetwork.sendLaptopResult(player, grid, payout, mask[0], 0);
    }

    private static boolean isValid(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || CasinoManager.isInCasino(player)) {
            return false;
        }
        BlockState state = player.serverLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof LaptopBlock)) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }

    private LaptopSlotManager() {
    }
}
