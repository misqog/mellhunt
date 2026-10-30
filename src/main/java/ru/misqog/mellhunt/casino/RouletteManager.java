package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.block.CasinoTableBlock;
import ru.misqog.mellhunt.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * European roulette on the roulette table.
 *
 * <p>The player selects one bet and spins. A number pays 36x, red/black,
 * even/odd and low/high pay 2x (stake included). The spin costs one gold
 * ingot. The result is decided on the server; the client animates the wheel
 * to the number, and the gold is paid only after that animation.</p>
 */
public final class RouletteManager {

    public static final int RED = 0;
    public static final int BLACK = 1;
    public static final int EVEN = 2;
    public static final int ODD = 3;
    public static final int LOW = 4;
    public static final int HIGH = 5;
    public static final int NUMBER = 6;

    public static final int KIND_COUNT = 7;

    private static final int MAX_DISTANCE_SQR = 64;
    private static final int HISTORY_TICKS = 5 * 60 * 20;
    private static final int MAX_HISTORY = 10;

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private record HistoryEntry(int number, long time) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Deque<HistoryEntry>>> HISTORY =
            new HashMap<>();

    public static void open(ServerPlayer player, BlockPos pos) {
        if (!isValidTable(player, pos)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ModNetwork.sendOpenRoulette(player, pos, history(level, pos));
    }

    public static void spin(ServerPlayer player, BlockPos pos, int kind, int number, boolean turbo) {
        if (!isValidTable(player, pos)) {
            return;
        }
        if (kind < 0 || kind >= KIND_COUNT) {
            return;
        }
        if (kind == NUMBER && (number < 0 || number > 36)) {
            return;
        }
        if (kind != NUMBER) {
            number = 0;
        }

        boolean free = player.isCreative();
        if (!free && !CasinoMoney.takeGold(player)) {
            ModNetwork.sendRouletteResult(player, 0, kind, number, false, 0, 1,
                    history(player.serverLevel(), pos));
            player.displayClientMessage(
                    Component.translatable("roulette.mellhunt.no_gold"), true);
            return;
        }

        RandomSource random = player.getRandom();
        int winning = random.nextInt(37);
        boolean won = isWin(kind, number, winning);
        int payout = won ? (kind == NUMBER ? 36 : 2) : 0;

        ServerLevel level = player.serverLevel();
        record(level, pos, winning);

        // The gold stays in the "house" until the wheel animation is over.
        if (payout > 0) {
            PendingPayouts.schedule(player, new ItemStack(Items.GOLD_INGOT, payout),
                    turbo ? 45 : 125);
        }

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.1F);
        ModNetwork.sendRouletteResult(player, winning, kind, number, won, payout, 0,
                history(level, pos));
    }

    public static boolean isRed(int number) {
        return RED_NUMBERS.contains(number);
    }

    private static boolean isWin(int kind, int number, int winning) {
        return switch (kind) {
            case RED -> isRed(winning);
            case BLACK -> winning != 0 && !isRed(winning);
            case EVEN -> winning != 0 && winning % 2 == 0;
            case ODD -> winning % 2 == 1;
            case LOW -> winning >= 1 && winning <= 18;
            case HIGH -> winning >= 19 && winning <= 36;
            case NUMBER -> winning == number;
            default -> false;
        };
    }

    private static int[] history(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Deque<HistoryEntry>> tables = HISTORY.get(level.dimension());
        if (tables == null) {
            return new int[0];
        }
        Deque<HistoryEntry> history = tables.get(pos);
        if (history == null) {
            return new int[0];
        }
        long now = level.getGameTime();
        while (!history.isEmpty() && now - history.peekLast().time() > HISTORY_TICKS) {
            history.removeLast();
        }
        int[] numbers = new int[history.size()];
        int index = 0;
        for (HistoryEntry entry : history) {
            numbers[index++] = entry.number();
        }
        if (history.isEmpty()) {
            tables.remove(pos);
            if (tables.isEmpty()) {
                HISTORY.remove(level.dimension());
            }
        }
        return numbers;
    }

    private static void record(ServerLevel level, BlockPos pos, int number) {
        Map<BlockPos, Deque<HistoryEntry>> tables = HISTORY.computeIfAbsent(
                level.dimension(), key -> new HashMap<>());
        Deque<HistoryEntry> history = tables.computeIfAbsent(pos.immutable(),
                key -> new ArrayDeque<>());
        history.addFirst(new HistoryEntry(number, level.getGameTime()));
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    private static boolean isValidTable(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || CasinoManager.isInCasino(player)) {
            return false;
        }
        BlockState state = player.serverLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof CasinoTableBlock)) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }

    private RouletteManager() {
    }
}
