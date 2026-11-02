package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: the "you are broke" countdown. {@code seconds <= 0}
 * removes it from the screen.
 */
public class CasinoCountdownPacket {

    private final int seconds;

    public CasinoCountdownPacket(int seconds) {
        this.seconds = seconds;
    }

    public static void encode(CasinoCountdownPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.seconds);
    }

    public static CasinoCountdownPacket decode(FriendlyByteBuf buffer) {
        return new CasinoCountdownPacket(buffer.readVarInt());
    }

    public static void handle(CasinoCountdownPacket packet, Supplier<NetworkEvent.Context> context) {
        int seconds = packet.seconds;
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.showCasinoCountdown(seconds)));
        context.get().setPacketHandled(true);
    }
}
