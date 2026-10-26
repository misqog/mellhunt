package ru.misqog.mellhunt.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** Client-only bridge used by the network packets (never class-loaded on a server). */
public final class ClientPacketHandler {

    public static void openCasino(int winChance) {
        Minecraft.getInstance().setScreen(new CasinoScreen(winChance));
    }

    public static void playTrackedSound(int entityId, ResourceLocation soundId, SoundSource source,
                                        float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (entity == null) {
            return;
        }

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (sound == null) {
            return;
        }

        minecraft.getSoundManager().play(
                new EntityTrackingSound(entity, sound, source, volume, pitch));
    }

    /**
     * Plays a sound at the listener with no world position and no attenuation,
     * i.e. directly in the player's ears.
     */
    public static void playLocalSound(ResourceLocation soundId, float volume, float pitch) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (sound == null) {
            return;
        }

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void showResult(boolean won, int[] line, boolean bonusRound, boolean secondChance) {
        if (Minecraft.getInstance().screen instanceof CasinoScreen screen) {
            screen.receiveResult(won, line, bonusRound, secondChance);
        }
    }

    private ClientPacketHandler() {
    }
}
