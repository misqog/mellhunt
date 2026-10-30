package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.block.CasinoTableBlock;
import ru.misqog.mellhunt.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared European roulette on the 2x2 roulette table.
 *
 * <p>Every table has its own timer and bets:
 * <ul>
 *   <li>8 seconds wheel spin, then 5 seconds of result display,</li>
 *   <li>5 seconds countdown before betting,</li>
 *   <li>10 seconds of betting,</li>
 *   <li>automatic spin at the end of the betting window.</li>
 * </ul>
 * All players who have that table open see the same phase, timer and result.
 * There is no spin button and no turbo: the table runs by itself.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class RouletteManager {

    public static final int RED = 0;
    public static final int BLACK = 1;
    public static final int EVEN = 2;
    public static final int ODD = 3;
    public static final int LOW = 4;
    public static final int HIGH = 5;
    public static final int NUMBER = 6;

    public static final int KIND_COUNT = 7;

    public static final int PHASE_PREPARE = 0;
    public static final int PHASE_BET = 1;
    public static final int PHASE_RESULT = 2;

    public static final int PREPARE_TICKS = 5 * 20;
    public static final int BET_TICKS = 10 * 20;
    /** The wheel animation itself lasts 8 seconds. */
    public static final int SPIN_TICKS = 8 * 20;
    /** How long the stopped wheel and the result stay on screen. */
    public static final int RESULT_DISPLAY_TICKS = 5 * 20;
    /** 8 s spin + 5 s result display. */
    public static final int RESULT_TICKS = SPIN_TICKS + RESULT_DISPLAY_TICKS;

    private static final int MAX_DISTANCE_SQR = 8 * 8;
    private static final int HISTORY_TICKS = 5 * 60 * 20;
    private static final int MAX_HISTORY = 10;

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    public static final int POCKETS = 37;

    /**
     * Real European wheel order, clockwise from the green 0 at the top.
     * Red and black strictly alternate around the wheel.
     */
    private static final int[] WHEEL_ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
            10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };

    /** Number printed in the given pocket (0 is the top pocket). */
    public static int wheelNumber(int pocket) {
        return WHEEL_ORDER[Math.floorMod(pocket, POCKETS)];
    }

    /** Pocket that carries the given number, 0 at the top, clockwise. */
    public static int wheelPocket(int number) {
        for (int pocket = 0; pocket < POCKETS; pocket++) {
            if (WHEEL_ORDER[pocket] == number) {
                return pocket;
            }
        }
        return 0;
    }

    private record HistoryEntry(int number, long time) {
    }

    private record Bet(int kind, int number) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Deque<HistoryEntry>>> HISTORY =
            new HashMap<>();

    /** One independent timer/bet pool per table anchor. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, TableState>> TABLES = new HashMap<>();

    private static final class TableState {
        private final ServerLevel level;
        private final BlockPos pos;
        private final Set<UUID> viewers = new HashSet<>();
        private final Map<UUID, Bet> bets = new HashMap<>();
        private int phase = PHASE_PREPARE;
        private int ticks = PREPARE_TICKS;
        private int winning = -1;

        private TableState(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }

    private static Map<BlockPos, TableState> tables(ServerLevel level) {
        return TABLES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
    }

    public static int phaseTicks(int phase) {
        return switch (phase) {
            case PHASE_BET -> BET_TICKS;
            case PHASE_RESULT -> RESULT_TICKS;
            default -> PREPARE_TICKS;
        };
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        if (!isValidTable(player, pos)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        TableState state = tables(level).computeIfAbsent(pos.immutable(),
                key -> new TableState(level, key));
        state.viewers.add(player.getUUID());

        ModNetwork.sendOpenRoulette(player, pos, history(level, pos));
        sendState(player, level, state);
    }

    /** Places or changes the player's bet for the current round. */
    public static void bet(ServerPlayer player, BlockPos pos, int kind, int number) {
        if (!isValidTable(player, pos) || kind < 0 || kind >= KIND_COUNT) {
            return;
        }
        if (kind == NUMBER && (number < 0 || number > 36)) {
            return;
        }
        if (kind != NUMBER) {
            number = 0;
        }

        ServerLevel level = player.serverLevel();
        TableState state = tables(level).get(pos);
        if (state == null) {
            return;
        }
        if (state.phase != PHASE_BET) {
            sendState(player, level, state);
            return;
        }

        state.viewers.add(player.getUUID());
        UUID id = player.getUUID();
        if (!state.bets.containsKey(id) && !player.isCreative() && !CasinoMoney.takeGold(player)) {
            ModNetwork.sendRouletteResult(player, -1, kind, number, false, 0, 1,
                    history(level, pos));
            player.displayClientMessage(
                    Component.translatable("roulette.mellhunt.no_gold"), true);
            return;
        }

        state.bets.put(id, new Bet(kind, number));
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS,
                0.35F, 1.4F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        for (var dimensionEntry : new ArrayList<>(TABLES.entrySet())) {
            ServerLevel level = server.getLevel(dimensionEntry.getKey());
            if (level == null) {
                TABLES.remove(dimensionEntry.getKey());
                continue;
            }

            Map<BlockPos, TableState> tables = dimensionEntry.getValue();
            for (var tableEntry : new ArrayList<>(tables.entrySet())) {
                TableState state = tableEntry.getValue();
                if (!(level.getBlockState(state.pos).getBlock() instanceof CasinoTableBlock)) {
                    tables.remove(tableEntry.getKey());
                    continue;
                }
                pruneViewers(server, state);

                if (state.viewers.isEmpty()) {
                    tables.remove(tableEntry.getKey());
                    continue;
                }

                state.ticks--;
                if (state.ticks <= 0) {
                    advance(level, state);
                } else if (state.ticks % 20 == 0) {
                    broadcastState(level, state);
                }
            }

            if (tables.isEmpty()) {
                TABLES.remove(dimensionEntry.getKey());
            }
        }
    }

    private static void advance(ServerLevel level, TableState state) {
        switch (state.phase) {
            case PHASE_PREPARE -> {
                state.phase = PHASE_BET;
                state.ticks = BET_TICKS;
                state.winning = -1;
                broadcastState(level, state);
            }
            case PHASE_BET -> resolve(level, state);
            default -> {
                state.phase = PHASE_PREPARE;
                state.ticks = PREPARE_TICKS;
                state.winning = -1;
                broadcastState(level, state);
            }
        }
    }

    private static void resolve(ServerLevel level, TableState state) {
        RandomSource random = level.random;
        int winning = random.nextInt(37);

        state.phase = PHASE_RESULT;
        state.ticks = RESULT_TICKS;
        state.winning = winning;
        record(level, state.pos, winning);
        int[] history = history(level, state.pos);

        broadcastState(level, state);
        for (UUID id : state.viewers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }

            Bet bet = state.bets.get(id);
            if (bet == null) {
                ModNetwork.sendRouletteResult(player, winning, -1, 0, false, 0, 0, history);
                continue;
            }

            boolean won = isWin(bet.kind(), bet.number(), winning);
            int payout = won ? (bet.kind() == NUMBER ? 36 : 2) : 0;
            if (payout > 0) {
                PendingPayouts.schedule(player, new ItemStack(Items.GOLD_INGOT, payout),
                        SPIN_TICKS);
            }
            ModNetwork.sendRouletteResult(player, winning, bet.kind(), bet.number(),
                    won, payout, 0, history);
        }

        state.bets.clear();
        level.playSound(null, state.pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.1F);
    }

    private static void pruneViewers(MinecraftServer server, TableState state) {
        for (UUID id : new ArrayList<>(state.viewers)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null
                    || player.serverLevel() != state.level
                    || player.isSpectator()
                    || CasinoManager.isInCasino(player)
                    || player.distanceToSqr(Vec3.atCenterOf(state.pos)) > MAX_DISTANCE_SQR) {
                state.viewers.remove(id);
            }
        }
    }

    private static void broadcastState(ServerLevel level, TableState state) {
        int[] history = history(level, state.pos);
        for (UUID id : new ArrayList<>(state.viewers)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                ModNetwork.sendRouletteState(player, state.pos, state.phase, state.ticks,
                        state.winning, history);
            }
        }
    }

    private static void sendState(ServerPlayer player, ServerLevel level, TableState state) {
        ModNetwork.sendRouletteState(player, state.pos, state.phase, state.ticks,
                state.winning, history(level, state.pos));
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
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR * 4;
    }

    private RouletteManager() {
    }
}
