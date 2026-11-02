package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes every Mellstroy sound exclusive: when a new mod sound starts, the
 * previous one is stopped immediately. This is the single place that controls
 * overlap for chase phrases, "I see you", "didn't catch you", the kill sound,
 * the reels and the casino result.
 */
@Mod.EventBusSubscriber(
        modid = MellstroyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MellstroyAudio {

    private static final List<SoundInstance> ACTIVE = new ArrayList<>();

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !MellstroyMod.MOD_ID.equals(sound.getLocation().getNamespace())) {
            return;
        }

        SoundManager manager = Minecraft.getInstance().getSoundManager();

        // Kill everything that is still playing before this new sound starts.
        // The casino entry track is the one exception: it must be allowed to
        // finish, so it is never stopped by a later mod sound. A second entry
        // track, however, replaces the first one instead of overlapping it.
        boolean newIsEntry = isEntryTrack(sound);
        for (SoundInstance old : ACTIVE) {
            if (old != sound && manager.isActive(old)
                    && (!isEntryTrack(old) || newIsEntry)) {
                manager.stop(old);
            }
        }
        ACTIVE.removeIf(old -> old == sound || !manager.isActive(old));
        ACTIVE.add(sound);
    }

    /** The long casino entry track, which must play to the end. */
    private static boolean isEntryTrack(SoundInstance sound) {
        // The registry path is "mellhunt.poshla_voznya", not "poshla_voznya".
        return sound != null
                && ModSounds.POSHLA_VOZNYA.get().getLocation().equals(sound.getLocation());
    }

    /** Used when the casino screen closes so no mod sound is left hanging. */
    public static void stopAll() {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        for (SoundInstance sound : ACTIVE) {
            if (!isEntryTrack(sound) && manager.isActive(sound)) {
                manager.stop(sound);
            }
        }
        ACTIVE.removeIf(sound -> !isEntryTrack(sound) || !manager.isActive(sound));
    }

    private MellstroyAudio() {
    }
}
