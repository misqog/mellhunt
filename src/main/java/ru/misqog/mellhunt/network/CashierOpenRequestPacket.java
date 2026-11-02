package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.CasinoCashier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -&gt; server: the player clicked the casino cashier. */
public class CashierOpenRequestPacket {

    public CashierOpenRequestPacket() {
    }

    public static void encode(CashierOpenRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static CashierOpenRequestPacket decode(FriendlyByteBuf buffer) {
        return new CashierOpenRequestPacket();
    }

    public static void handle(CashierOpenRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> CasinoCashier.open(player));
        }
        ctx.setPacketHandled(true);
    }
}
