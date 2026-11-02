package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.CasinoCashier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -&gt; server: the player pressed one of the cashier's buttons. */
public class CashierTradePacket {

    /** The trade buttons, in the order the screen draws them. */
    public static final int ALL = 0;
    public static final int FIVE = 5;
    public static final int TEN = 10;
    public static final int TWENTY = 20;

    private final int exchanges;

    public CashierTradePacket(int exchanges) {
        this.exchanges = exchanges;
    }

    public static void encode(CashierTradePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.exchanges);
    }

    public static CashierTradePacket decode(FriendlyByteBuf buffer) {
        return new CashierTradePacket(buffer.readVarInt());
    }

    public static void handle(CashierTradePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        int exchanges = packet.exchanges;
        ctx.enqueueWork(() -> CasinoCashier.trade(player, exchanges));
        ctx.setPacketHandled(true);
    }
}
