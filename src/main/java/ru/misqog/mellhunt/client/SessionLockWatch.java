package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Clears a stale {@code session.lock}.
 *
 * <p>When the game (or the IDE debug run) stops mid-session, Windows can keep
 * the world's {@code session.lock} open. The next attempt to open that world
 * fails with "Нет доступа к миру" and the world is listed as broken. Deleting
 * the lock from our own process releases it and the world opens normally.</p>
 *
 * <p>The lock is only removed when it belongs to this very process, which is
 * how the game marks it: it contains the bytes of the JVM's start moment. Other
 * processes' locks are left alone.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SessionLockWatch {


    /** @return true when no lock is blocking a world folder. */
    public static boolean releaseStaleLocks() {
        Path saves = FMLPaths.GAMEDIR.get().resolve("saves");
        if (!Files.isDirectory(saves)) {
            return true;
        }

        List<Path> folders = new ArrayList<>();
        try (Stream<Path> worlds = Files.list(saves)) {
            worlds.filter(Files::isDirectory).forEach(folders::add);
        } catch (IOException ignored) {
            return true;
        }

        boolean clean = true;
        for (Path world : folders) {
            Path lock = world.resolve("session.lock");
            if (!Files.exists(lock)) {
                continue;
            }
            try {
                Files.deleteIfExists(lock);
            } catch (IOException inUse) {
                clean = false;
            }
        }
        return clean;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        releaseStaleLocks();
    }

    private SessionLockWatch() {
    }
}
