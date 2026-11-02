package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.block.PokerTableBlock;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.network.PokerStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jacks-or-Better video poker on the big poker table.
 *
 * <p>The server holds the deck between DEAL and DRAW; the client only sends
 * which cards to hold. The hand is evaluated on the server, and the payout is
 * granted immediately, so closing the screen cannot lose a win.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class PokerManager {

    private static final int MAX_DISTANCE_SQR = 64;

    private static final class Session {
        private final BlockPos pos;
        private final int[] hand;
        private final int[] deck;
        private int next;

        private Session(BlockPos pos, int[] hand, int[] deck) {
            this.pos = pos;
            this.hand = hand;
            this.deck = deck;
            this.next = hand.length;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static void open(ServerPlayer player, BlockPos pos) {
        if (!isValidTable(player, pos)) {
            return;
        }
        ModNetwork.sendOpenPoker(player, pos);
        // Re-send whatever is already in progress instead of resetting it: a
        // player who closed the screen must see the game, not an idle table.
        Session session = SESSIONS.get(player.getUUID());
        Session current = session;
        if (current == null) {
            ModNetwork.sendPokerState(player,
                    new PokerStatePacket(PokerStatePacket.IDLE, new int[0], 0, 0, -1, 0));
            return;
        }
        ModNetwork.sendPokerState(player,
                new PokerStatePacket(PokerStatePacket.HOLD, current.hand, 0, 0, -1, 0));
    }

    public static void deal(ServerPlayer player, BlockPos pos) {
        if (!isValidTable(player, pos)) {
            return;
        }
        boolean free = player.isCreative();
        // The wager is registered first: the inventory is empty until the
        // result, and the death timer must not start before it is shown.
        ActiveBets.place(player.getUUID());
        if (!free && !CasinoMoney.takeGold(player)) {
            ActiveBets.resolve(player.getUUID());
            ModNetwork.sendPokerState(player,
                    new PokerStatePacket(PokerStatePacket.IDLE, new int[0], 0, 0, -1, 1));
            return;
        }

        int[] deck = PokerHands.shuffledDeck(player.getRandom());
        int[] hand = new int[5];
        System.arraycopy(deck, 0, hand, 0, 5);
        SESSIONS.put(player.getUUID(), new Session(pos.immutable(), hand, deck));

        ModNetwork.sendPokerState(player,
                new PokerStatePacket(PokerStatePacket.HOLD, hand, 0, 0, -1, 0));
        player.serverLevel().playSound(null, pos, SoundEvents.ITEM_PICKUP,
                SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    public static void draw(ServerPlayer player, BlockPos pos, int heldMask) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            ModNetwork.sendPokerState(player,
                    new PokerStatePacket(PokerStatePacket.IDLE, new int[0], 0, 0, -1, 2));
            return;
        }
        if (!session.pos.equals(pos)) {
            // A draw at a different table: this is a new game at that table,
            // not a reason to kill the running bet.
            ModNetwork.sendPokerState(player,
                    new PokerStatePacket(PokerStatePacket.IDLE, new int[0], 0, 0, -1, 2));
            return;
        }
        if (!isValidTable(player, pos)) {
            ModNetwork.sendPokerState(player,
                    new PokerStatePacket(PokerStatePacket.IDLE, new int[0], 0, 0, -1, 2));
            return;
        }

        heldMask &= 31;
        for (int i = 0; i < 5; i++) {
            if ((heldMask & (1 << i)) == 0) {
                session.hand[i] = session.deck[session.next++];
            }
        }

        int category = PokerHands.evaluate(session.hand);
        int payout = PokerHands.payout(category);
        // Nothing is paid here: the client tells the server when the cards are
        // actually revealed, so a win is never hinted at by the inventory or by
        // a payout that arrived before the player saw the hand.

        SESSIONS.remove(player.getUUID());
        if (payout <= 0) {
            // A losing hand ends the wager right away.
            ActiveBets.resolve(player.getUUID());
        }
        ModNetwork.sendPokerState(player,
                new PokerStatePacket(PokerStatePacket.RESULT, session.hand,
                        heldMask, payout, category, 0));
        // Only the neutral card flip, so a win or a loss is not telegraphed.
        player.serverLevel().playSound(null, pos, SoundEvents.ITEM_PICKUP,
                SoundSource.BLOCKS, 0.5F, 0.9F);
    }

    /** Client -&gt; server: the result cards are visible; pay the win now. */
    public static void claimPayout(ServerPlayer player, BlockPos pos, int payout) {
        ActiveBets.resolve(player.getUUID());
        if (payout <= 0) {
            return;
        }
        PendingPayouts.schedule(player,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, payout), 1);
    }

    private static boolean isValidTable(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || CasinoManager.isInCasino(player)) {
            return false;
        }
        BlockState state = player.serverLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof PokerTableBlock)) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        // Closing the game must not leave the bet in play forever.
        ActiveBets.clear(event.getEntity().getUUID());
    }

    private PokerManager() {
    }
}
