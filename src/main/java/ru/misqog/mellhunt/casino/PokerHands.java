package ru.misqog.mellhunt.casino;

import net.minecraft.util.RandomSource;

/**
 * Five-card poker (Jacks or Better video poker).
 *
 * <p>A card is encoded as {@code rank * 4 + suit}: ranks 2..14 (11=J, 12=Q,
 * 13=K, 14=A) and suits 0=spades, 1=hearts, 2=diamonds, 3=clubs.</p>
 */
public final class PokerHands {

    public static final int HIGH_CARD = 0;
    public static final int JACKS_OR_BETTER = 1;
    public static final int TWO_PAIR = 2;
    public static final int THREE_OF_A_KIND = 3;
    public static final int STRAIGHT = 4;
    public static final int FLUSH = 5;
    public static final int FULL_HOUSE = 6;
    public static final int FOUR_OF_A_KIND = 7;
    public static final int STRAIGHT_FLUSH = 8;
    public static final int ROYAL_FLUSH = 9;

    public static final int CATEGORY_COUNT = 10;

    public static int rank(int card) {
        return card >> 2;
    }

    public static int suit(int card) {
        return card & 3;
    }

    public static int card(int rank, int suit) {
        return rank * 4 + suit;
    }

    public static int evaluate(int[] cards) {
        int[] counts = new int[15];
        int[] suits = new int[4];
        for (int card : cards) {
            int r = rank(card);
            if (r < 2 || r > 14) {
                continue;
            }
            counts[r]++;
            suits[suit(card)]++;
        }

        boolean flush = false;
        for (int count : suits) {
            if (count == 5) {
                flush = true;
            }
        }

        int distinct = 0;
        int min = 99;
        int max = 0;
        int pairs = 0;
        boolean three = false;
        boolean four = false;
        boolean jackPair = false;
        for (int r = 2; r <= 14; r++) {
            if (counts[r] == 0) {
                continue;
            }
            distinct++;
            min = Math.min(min, r);
            max = Math.max(max, r);
            if (counts[r] == 4) {
                four = true;
            } else if (counts[r] == 3) {
                three = true;
            } else if (counts[r] == 2) {
                pairs++;
                if (r >= 11) {
                    jackPair = true;
                }
            }
        }

        boolean straight = distinct == 5 && (max - min == 4
                || (counts[14] > 0 && counts[2] > 0 && counts[3] > 0
                    && counts[4] > 0 && counts[5] > 0));

        if (straight && flush) {
            return min == 10 ? ROYAL_FLUSH : STRAIGHT_FLUSH;
        }
        if (four) {
            return FOUR_OF_A_KIND;
        }
        if (three && pairs == 1) {
            return FULL_HOUSE;
        }
        if (flush) {
            return FLUSH;
        }
        if (straight) {
            return STRAIGHT;
        }
        if (three) {
            return THREE_OF_A_KIND;
        }
        if (pairs >= 2) {
            return TWO_PAIR;
        }
        if (jackPair) {
            return JACKS_OR_BETTER;
        }
        return HIGH_CARD;
    }

    /** Payout in gold ingots for a one-ingot bet. */
    public static int payout(int category) {
        return switch (category) {
            case ROYAL_FLUSH -> 100;
            case STRAIGHT_FLUSH -> 50;
            case FOUR_OF_A_KIND -> 25;
            case FULL_HOUSE -> 9;
            case FLUSH -> 6;
            case STRAIGHT -> 4;
            case THREE_OF_A_KIND -> 3;
            case TWO_PAIR -> 2;
            case JACKS_OR_BETTER -> 1;
            default -> 0;
        };
    }

    public static String nameKey(int category) {
        return switch (category) {
            case ROYAL_FLUSH -> "poker.mellhunt.hand.royal";
            case STRAIGHT_FLUSH -> "poker.mellhunt.hand.straight_flush";
            case FOUR_OF_A_KIND -> "poker.mellhunt.hand.four";
            case FULL_HOUSE -> "poker.mellhunt.hand.full_house";
            case FLUSH -> "poker.mellhunt.hand.flush";
            case STRAIGHT -> "poker.mellhunt.hand.straight";
            case THREE_OF_A_KIND -> "poker.mellhunt.hand.three";
            case TWO_PAIR -> "poker.mellhunt.hand.two_pair";
            case JACKS_OR_BETTER -> "poker.mellhunt.hand.jacks";
            default -> "poker.mellhunt.hand.high";
        };
    }

    public static int[] shuffledDeck(RandomSource random) {
        int[] deck = new int[52];
        int index = 0;
        for (int r = 2; r <= 14; r++) {
            for (int s = 0; s < 4; s++) {
                deck[index++] = card(r, s);
            }
        }
        for (int i = deck.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = deck[i];
            deck[i] = deck[j];
            deck[j] = tmp;
        }
        return deck;
    }

    private PokerHands() {
    }
}
