package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.PokerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -&gt; server: the result cards are on screen, so the win may be paid.
 * The server re-validates the amount and the table before handing out gold.
 */
public class PokerClaimPacket {

    private final BlockPos pos;
    private final int payout;

    public PokerClaimPacket(BlockPos pos, int payout) {
        this.pos = pos.immutable();
        this.payout = payout;
    }

    public static void encode(PokerClaimPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.payout);
    }

    public static PokerClaimPacket decode(FriendlyByteBuf buffer) {
        return new PokerClaimPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(PokerClaimPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        BlockPos pos = packet.pos;
        int payout = packet.payout;
        ctx.enqueueWork(() -> PokerManager.claimPayout(player, pos, payout));
        ctx.setPacketHandled(true);
    }
}
