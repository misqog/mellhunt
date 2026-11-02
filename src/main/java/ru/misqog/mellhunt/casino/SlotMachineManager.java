package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.block.SlotMachineBlock;
import ru.misqog.mellhunt.config.MellstroyConfig;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The playable slot machine block.
 *
 * <p>One right-click opens the machine and one spin costs a gold ingot
 * (creative players spin for free). The result is decided immediately; the
 * prize is put into the inventory right away and the client only plays the
 * reel animation and the win/lose sound.</p>
 *
 * <p>This is deliberately separate from {@link CasinoManager}: the death
 * casino stakes the player's life, while this machine only eats the bet.
 * There is no session to wait for, so closing the screen or logging out can
 * never lose a prize that was already won.</p>
 */
public final class SlotMachineManager {

    private static final int MAX_DISTANCE_SQR = 64;

    private static final class Prize {
        private final Item item;
        private final int min;
        private final int max;
        private final int weight;

        private Prize(Item item, int min, int max, int weight) {
            this.item = item;
            this.min = min;
            this.max = max;
            this.weight = weight;
        }
    }

    /** Built lazily so the mod's own spawn egg is already registered. */
    private static List<Prize> prizes;

    private static List<Prize> prizes() {
        if (prizes == null) {
            // Gold always pays at least one ingot plus leftover nuggets, so the
            // smallest gold win is a decent hit instead of a single nugget.
            prizes = List.of(
                    new Prize(Items.GOLD_NUGGET, 9, 17, 38),
                    new Prize(Items.GOLD_INGOT, 2, 4, 24),
                    new Prize(Items.DIAMOND, 1, 2, 16),
                    new Prize(Items.EMERALD, 2, 4, 10),
                    new Prize(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 7),
                    new Prize(ModItems.FAT_CAT_SPAWN_EGG.get(), 1, 1, 3),
                    new Prize(Items.NETHERITE_INGOT, 1, 1, 2));
        }
        return prizes;
    }

    public static int winChance() {
        return Math.max(1, Math.min(99, MellstroyConfig.slotMachineWinChance()));
    }

    /** Called on the server when the player right-clicks the machine. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || CasinoManager.isInCasino(player)) {
            return;
        }
        if (!(player.serverLevel().getBlockState(pos).getBlock() instanceof SlotMachineBlock)) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            return;
        }
        if (!player.isCreative() && !CasinoMoney.hasGold(player)) {
            player.displayClientMessage(
                    Component.translatable("slot.mellhunt.no_gold").withStyle(ChatFormatting.GOLD), true);
            return;
        }

        player.serverLevel().playSound(null, pos, SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS, 0.5F, 1.2F);
        ModNetwork.sendOpenSlotMachine(player, pos, winChance());
    }

    /** Client -> server: the SPIN button was pressed. */
    public static void spin(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || CasinoManager.isInCasino(player)) {
            return;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof SlotMachineBlock)) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            return;
        }

        boolean free = player.isCreative();
        if (!free) {
            // The bet is registered first: the inventory is empty while the
            // reels spin, and the death timer must not start before the result.
            ActiveBets.place(player.getUUID());
            if (!CasinoMoney.takeGold(player)) {
                ActiveBets.resolve(player.getUUID());
                player.displayClientMessage(
                        Component.translatable("slot.mellhunt.no_gold").withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }

        RandomSource random = player.getRandom();
        boolean won = random.nextInt(100) < winChance();
        int[] line = won
                ? CasinoSymbols.randomWinning(random)
                : CasinoSymbols.randomLosing(random);

        ItemStack prize = ItemStack.EMPTY;
        if (won) {
            prize = rollPrize(random);
            // Pay after the reels have stopped, not while they are still spinning.
            int delay = CasinoTiming.resultStartTicks(CasinoTiming.SPIN_TICKS) + 20;
            if (prize.is(Items.GOLD_NUGGET) || prize.is(Items.GOLD_INGOT)) {
                // Gold is always paid as ingots and nuggets together, so a win
                // is never just a single lonely nugget.
                int nuggets = prize.is(Items.GOLD_NUGGET) ? prize.getCount() : prize.getCount() * 9;
                payGold(player, nuggets, delay);
                prize = goldPrizeStack(nuggets);
            } else {
                PendingPayouts.schedule(player, prize.copy(), delay);
            }
        } else {
            // A loss settles the bet right away; a win settles with the payout.
            ActiveBets.resolve(player.getUUID());
        }

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.8F);
        ModNetwork.sendSlotResult(player, won, line, prize);
    }

    /** Splits a gold win into ingots and nuggets and queues both stacks. */
    private static void payGold(ServerPlayer player, int nuggets, int delay) {
        int ingots = nuggets / 9;
        int rest = nuggets % 9;
        if (ingots > 0) {
            PendingPayouts.schedule(player, new ItemStack(Items.GOLD_INGOT, ingots), delay);
        }
        if (rest > 0) {
            PendingPayouts.schedule(player, new ItemStack(Items.GOLD_NUGGET, rest), delay);
        }
    }

    /** What the screen shows for a gold win: the ingots, or the nuggets if any. */
    private static ItemStack goldPrizeStack(int nuggets) {
        int ingots = nuggets / 9;
        return ingots > 0
                ? new ItemStack(Items.GOLD_INGOT, ingots)
                : new ItemStack(Items.GOLD_NUGGET, nuggets);
    }

    private static ItemStack rollPrize(RandomSource random) {
        int total = 0;
        for (Prize prize : prizes()) {
            total += prize.weight;
        }

        int roll = random.nextInt(total);
        for (Prize prize : prizes()) {
            if (roll < prize.weight) {
                int count = prize.min + random.nextInt(prize.max - prize.min + 1);
                return new ItemStack(prize.item, count);
            }
            roll -= prize.weight;
        }

        return new ItemStack(Items.GOLD_NUGGET, 12);
    }

    private SlotMachineManager() {
    }
}
