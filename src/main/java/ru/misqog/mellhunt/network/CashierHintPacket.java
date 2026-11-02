package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: the cashier is online and may be talked to again. While
 * the entry track is playing she is closed, so the title is not covered.
 */
public class CashierHintPacket {

    private final boolean enabled;

    public CashierHintPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(CashierHintPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
    }

    public static CashierHintPacket decode(FriendlyByteBuf buffer) {
        return new CashierHintPacket(buffer.readBoolean());
    }

    public static void handle(CashierHintPacket packet, Supplier<NetworkEvent.Context> context) {
        boolean enabled = packet.enabled;
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.setCashierEnabled(enabled)));
        context.get().setPacketHandled(true);
    }
}
