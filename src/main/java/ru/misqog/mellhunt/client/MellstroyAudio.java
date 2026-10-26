package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
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
        for (SoundInstance old : ACTIVE) {
            if (old != sound && manager.isActive(old)) {
                manager.stop(old);
            }
        }
        ACTIVE.removeIf(old -> old == sound || !manager.isActive(old));
        ACTIVE.add(sound);
    }

    /** Used when the casino screen closes so no mod sound is left hanging. */
    public static void stopAll() {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        for (SoundInstance sound : ACTIVE) {
            if (manager.isActive(sound)) {
                manager.stop(sound);
            }
        }
        ACTIVE.clear();
    }

    private MellstroyAudio() {
    }
}
