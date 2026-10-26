package ru.misqog.mellhunt.casino;

import net.minecraft.util.RandomSource;

/**
 * Shared slot-symbol ids and the rules used to build a result line.
 *
 * <p>The server decides whether the spin is a win. The line is generated so
 * that the player can see a real slot-machine result: any line with a 7 (a
 * wild/scatter) or any three of a kind is a win. Losing lines contain no 7 and
 * are never three of a kind.</p>
 */
public final class CasinoSymbols {

    public static final int SEVEN = 0;
    public static final int DIAMOND = 1;
    public static final int GOLD = 2;
    public static final int EMERALD = 3;
    public static final int NETHERITE = 4;
    public static final int APPLE = 5;

    public static final int COUNT = 6;

    private static final int NON_SEVEN_COUNT = COUNT - 1;

    public static boolean isWinning(int[] line) {
        if (line == null || line.length != 3) {
            return false;
        }
        for (int symbol : line) {
            if (symbol == SEVEN) {
                return true;
            }
        }
        return line[0] == line[1] && line[1] == line[2];
    }

    /** Builds a line that visibly matches a winning spin. */
    public static int[] randomWinning(RandomSource random) {
        int style = random.nextInt(3);

        if (style == 0) {
            int symbol = random.nextInt(COUNT);
            return new int[]{symbol, symbol, symbol};
        }

        if (style == 1) {
            int[] line = {nonSeven(random), nonSeven(random), nonSeven(random)};
            line[random.nextInt(3)] = SEVEN;
            return line;
        }

        int[] line = {SEVEN, SEVEN, nonSeven(random)};
        shuffle(line, random);
        return line;
    }

    /** Builds a line that visibly matches a losing spin. */
    public static int[] randomLosing(RandomSource random) {
        int first;
        int second;
        int third;
        do {
            first = nonSeven(random);
            second = nonSeven(random);
            third = nonSeven(random);
        } while (first == second && second == third);
        return new int[]{first, second, third};
    }

    private static int nonSeven(RandomSource random) {
        return 1 + random.nextInt(NON_SEVEN_COUNT);
    }

    private static void shuffle(int[] line, RandomSource random) {
        for (int i = line.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = line[i];
            line[i] = line[j];
            line[j] = tmp;
        }
    }

    private CasinoSymbols() {
    }
}
