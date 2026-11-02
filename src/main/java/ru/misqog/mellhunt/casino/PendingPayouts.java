package ru.misqog.mellhunt.casino;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Delayed casino prizes.
 *
 * <p>The result is decided on the server immediately, but the visual spin /
 * card reveal lasts a while. Paying into the inventory only when the animation
 * is over makes the win feel earned and prevents the inventory from changing
 * while the player is still watching the reels.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class PendingPayouts {

    private static final long EXPIRE_TICKS = 20L * 60L * 5L;

    private record Entry(UUID player, ItemStack stack, long readyAt, long expiresAt) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void schedule(ServerPlayer player, ItemStack stack, int delayTicks) {
        if (stack.isEmpty()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        long readyAt = now + Math.max(1, delayTicks);
        ENTRIES.add(new Entry(player.getUUID(), stack.copy(),
                readyAt, readyAt + EXPIRE_TICKS));
    }

    /**
     * How many gold ingots are waiting for this player. Used so the casino does
     * not start its death timer on a player whose winnings are still in flight.
     */
    public static int pendingGold(UUID player) {
        int ingots = 0;
        for (Entry entry : ENTRIES) {
            if (entry.player().equals(player) && entry.stack().is(Items.GOLD_INGOT)) {
                ingots += entry.stack().getCount();
            }
        }
        return ingots;
    }

    /** True when a payout (ingots, nuggets or a prize) is still on its way. */
    public static boolean hasPending(UUID player) {
        for (Entry entry : ENTRIES) {
            if (entry.player().equals(player)) {
                return true;
            }
        }
        return false;
    }

    /** Throws away every payout that was still in flight for this player. */
    public static void discard(UUID player) {
        ENTRIES.removeIf(entry -> entry.player().equals(player));
    }

    /**
     * Pays everything that is ready right now, without waiting for the next
     * tick. Used when a round ends so the winnings land immediately.
     */
    public static void payNow(ServerPlayer player, long now) {
        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.player().equals(player.getUUID()) && now >= entry.readyAt()) {
                CasinoMoney.give(player, entry.stack());
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ENTRIES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();

        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (now < entry.readyAt()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.player());
            if (player != null) {
                CasinoMoney.give(player, entry.stack());
                iterator.remove();
            } else if (now >= entry.expiresAt()) {
                iterator.remove();
            }
        }
    }

    private PendingPayouts() {
    }
}
