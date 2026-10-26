package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: play a sound directly in the player's ears. Unlike a
 * normal positional sound it is not attached to a block position, so walking
 * away does not fade it out and it always comes from the listener.
 */
public class PlayerSoundPacket {

    private final ResourceLocation sound;
    private final float volume;
    private final float pitch;

    public PlayerSoundPacket(ResourceLocation sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(PlayerSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.sound);
        buffer.writeFloat(packet.volume);
        buffer.writeFloat(packet.pitch);
    }

    public static PlayerSoundPacket decode(FriendlyByteBuf buffer) {
        return new PlayerSoundPacket(buffer.readResourceLocation(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(PlayerSoundPacket packet, Supplier<NetworkEvent.Context> context) {
        ResourceLocation sound = packet.sound;
        float volume = packet.volume;
        float pitch = packet.pitch;
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.playLocalSound(sound, volume, pitch)));
        context.get().setPacketHandled(true);
    }
}
