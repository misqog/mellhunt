package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client: the player walked up to the casino cashier. The client
 * then opens the nugget exchange screen and talks to her directly.
 */
public class OpenCashierPacket {

    public OpenCashierPacket() {
    }

    public static void encode(OpenCashierPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenCashierPacket decode(FriendlyByteBuf buffer) {
        return new OpenCashierPacket();
    }

    public static void handle(OpenCashierPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientPacketHandler::openCashier));
        context.get().setPacketHandled(true);
    }
}
