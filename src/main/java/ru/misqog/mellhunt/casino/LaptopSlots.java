package ru.misqog.mellhunt.casino;

import net.minecraft.util.RandomSource;

/**
 * The five-reel, three-row laptop slot game.
 *
 * <p>Five fixed paylines cross the 5 x 3 grid. A line pays for three or more
 * consecutive symbols from the left; the payout is in gold nuggets. The
 * symbols are the "online casino" set: banana, monkey, dog, Zeus, coin, seven.</p>
 */
public final class LaptopSlots {

    public static final int BANANA = 0;
    public static final int MONKEY = 1;
    public static final int DOG = 2;
    public static final int ZEUS = 3;
    public static final int COIN = 4;
    public static final int SEVEN = 5;
    public static final int COUNT = 6;

    public static final int REELS = 5;
    public static final int ROWS = 3;
    public static final int CELLS = REELS * ROWS;

    public static final int[][] LINES = {
            {1, 1, 1, 1, 1},
            {0, 0, 0, 0, 0},
            {2, 2, 2, 2, 2},
            {0, 1, 2, 1, 0},
            {2, 1, 0, 1, 2}
    };

    /** Three-of-a-kind payout in gold nuggets. */
    public static final int[] BASE = {1, 2, 3, 5, 8, 12};

    public static int[] randomGrid(RandomSource random) {
        int[] grid = new int[CELLS];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = random.nextInt(COUNT);
        }
        return grid;
    }

    /**
     * Evaluates every payline. Win lines are marked in the returned mask; the
     * return value is the total payout in gold nuggets.
     */
    public static int evaluate(int[] grid, int[] winMask) {
        return evaluate(grid, winMask, LINES.length);
    }

    /**
     * Evaluates only the first {@code lines} paylines (1..5), like selecting
     * the number of active lines on a real slot machine.
     */
    public static int evaluate(int[] grid, int[] winMask, int lines) {
        if (grid == null || grid.length != CELLS) {
            if (winMask != null && winMask.length > 0) {
                winMask[0] = 0;
            }
            return 0;
        }
        int active = Math.max(1, Math.min(LINES.length, lines));
        int mask = 0;
        int total = 0;
        for (int line = 0; line < active; line++) {
            int symbol = grid[LINES[line][0]];
            int count = 1;
            for (int reel = 1; reel < REELS; reel++) {
                if (grid[reel * ROWS + LINES[line][reel]] != symbol) {
                    break;
                }
                count++;
            }
            if (count >= 3) {
                int multiplier = count == 3 ? 1 : count == 4 ? 3 : 8;
                total += BASE[symbol] * multiplier;
                mask |= 1 << line;
            }
        }
        if (winMask != null && winMask.length > 0) {
            winMask[0] = mask;
        }
        return total;
    }

    public static String nameKey(int symbol) {
        return switch (symbol) {
            case BANANA -> "laptop.slots.symbol.banana";
            case MONKEY -> "laptop.slots.symbol.monkey";
            case DOG -> "laptop.slots.symbol.dog";
            case ZEUS -> "laptop.slots.symbol.zeus";
            case COIN -> "laptop.slots.symbol.coin";
            default -> "laptop.slots.symbol.seven";
        };
    }

    private LaptopSlots() {
    }
}
