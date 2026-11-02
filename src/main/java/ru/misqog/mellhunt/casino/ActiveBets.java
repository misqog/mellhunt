package ru.misqog.mellhunt.casino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the bets that are currently on the table.
 *
 * <p>When the last ingot goes into a spin the inventory is empty for a few
 * seconds, but the player is not broke: the wager is still in play. The death
 * timer must wait until every round has been resolved, otherwise a winning
 * hand would kill the player before its payout arrives.</p>
 */
public final class ActiveBets {

    private static final Map<UUID, Integer> BETS = new HashMap<>();

    /** Registers a new wager that will be resolved by a payout or a loss. */
    public static void place(UUID player) {
        BETS.merge(player, 1, Integer::sum);
    }

    /** Removes one wager once its round is over. */
    public static void resolve(UUID player) {
        Integer count = BETS.get(player);
        if (count == null || count <= 1) {
            BETS.remove(player);
        } else {
            BETS.put(player, count - 1);
        }
    }

    /** True while the player still has money in a running round. */
    public static boolean hasBet(UUID player) {
        return BETS.containsKey(player);
    }

    /** Forgets everything, e.g. when the casino session ends. */
    public static void clear(UUID player) {
        BETS.remove(player);
    }

    private ActiveBets() {
    }
}
