package com.mellstroy.horror.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A positional sound that follows a live entity instead of staying at the
 * position where it was first played. The sound engine asks this instance for
 * its coordinates every tick, so a moving Mellstroy is always heard where he
 * actually is.
 */
public final class EntityTrackingSound extends AbstractTickableSoundInstance {

    private final Entity entity;

    public EntityTrackingSound(Entity entity, SoundEvent sound, SoundSource source, float volume, float pitch) {
        super(sound, source, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.volume = volume;
        this.pitch = pitch;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.updatePosition();
    }

    @Override
    public void tick() {
        if (this.entity.isRemoved() || !this.entity.isAlive()) {
            this.stop();
            return;
        }
        this.updatePosition();
    }

    private void updatePosition() {
        this.x = this.entity.getX();
        this.y = this.entity.getY() + this.entity.getBbHeight() * 0.5D;
        this.z = this.entity.getZ();
    }
}
