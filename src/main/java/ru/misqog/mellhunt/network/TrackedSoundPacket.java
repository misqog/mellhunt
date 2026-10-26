package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: start a sound at an entity and let the client keep the
 * sound at that entity's current position while it plays.
 */
public class TrackedSoundPacket {

    private final int entityId;
    private final ResourceLocation sound;
    private final SoundSource source;
    private final float volume;
    private final float pitch;

    public TrackedSoundPacket(int entityId, ResourceLocation sound, SoundSource source, float volume, float pitch) {
        this.entityId = entityId;
        this.sound = sound;
        this.source = source;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(TrackedSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeResourceLocation(packet.sound);
        buffer.writeByte(packet.source.ordinal());
        buffer.writeFloat(packet.volume);
        buffer.writeFloat(packet.pitch);
    }

    public static TrackedSoundPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        ResourceLocation sound = buffer.readResourceLocation();
        int sourceOrdinal = buffer.readUnsignedByte();
        SoundSource[] sources = SoundSource.values();
        SoundSource source = sourceOrdinal < sources.length ? sources[sourceOrdinal] : SoundSource.NEUTRAL;
        float volume = buffer.readFloat();
        float pitch = buffer.readFloat();
        return new TrackedSoundPacket(entityId, sound, source, volume, pitch);
    }

    public static void handle(TrackedSoundPacket packet, Supplier<NetworkEvent.Context> context) {
        int entityId = packet.entityId;
        ResourceLocation sound = packet.sound;
        SoundSource source = packet.source;
        float volume = packet.volume;
        float pitch = packet.pitch;
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.playTrackedSound(entityId, sound, source, volume, pitch)));
        context.get().setPacketHandled(true);
    }
}
