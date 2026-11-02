package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: show the centred "mellstroy.game" title for the duration
 * of the casino entry track.
 */
public class CasinoEntryPacket {

    private final int durationTicks;

    public CasinoEntryPacket(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public static void encode(CasinoEntryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.durationTicks);
    }

    public static CasinoEntryPacket decode(FriendlyByteBuf buffer) {
        return new CasinoEntryPacket(buffer.readVarInt());
    }

    public static void handle(CasinoEntryPacket packet, Supplier<NetworkEvent.Context> context) {
        int duration = packet.durationTicks;
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.showCasinoEntry(duration)));
        context.get().setPacketHandled(true);
    }
}
