package ru.misqog.mellhunt.spawn;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.MellstroyEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * After the casino, Mellstroy disappears for a random 3-7 minutes and comes
 * back far away from every player. Natural spawning is suspended while this
 * timer is pending, so the return is always a single, quiet far spawn.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class MellstroyRespawnManager {

    private static final int MIN_DELAY_TICKS = 3 * 60 * 20;
    private static final int MAX_DELAY_TICKS = 7 * 60 * 20;
    private static final int FAR_MIN_DISTANCE = 100;
    private static final int FAR_MAX_DISTANCE = 180;
    private static final int RETRY_TICKS = 5 * 20;
    private static final int FAR_SPAWN_GRACE_TICKS = 10 * 20;

    private static long respawnAt = -1L;
    private static long nextAttemptAt = 0L;

    public static void schedule(MinecraftServer server) {
        RandomSource random = server.overworld().random;
        int delay = MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        respawnAt = server.overworld().getGameTime() + delay;
        nextAttemptAt = respawnAt;
    }

    public static boolean isPending() {
        return respawnAt >= 0L;
    }

    public static void cancel() {
        respawnAt = -1L;
        nextAttemptAt = 0L;
    }

    /** @return true if he was actually respawned during this tick. */
    public static boolean tick(MinecraftServer server) {
        if (!isPending()) {
            return false;
        }

        long now = server.overworld().getGameTime();
        if (now < respawnAt || now < nextAttemptAt) {
            return false;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            nextAttemptAt = now + RETRY_TICKS;
            return false;
        }

        ServerPlayer anchor = players.get(server.overworld().random.nextInt(players.size()));
        ServerLevel level = anchor.serverLevel();

        if (MellstroySpawner.trySpawn(level, anchor, FAR_MIN_DISTANCE, FAR_MAX_DISTANCE,
                true, false, FAR_SPAWN_GRACE_TICKS)) {
            cancel();
            return true;
        }

        nextAttemptAt = now + RETRY_TICKS;
        return false;
    }

    /** Convenience used by the casino code. */
    public static void scheduleAfterCasino(MinecraftServer server) {
        if (server != null) {
            schedule(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        cancel();
    }

    private MellstroyRespawnManager() {
    }
}
