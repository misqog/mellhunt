package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.PokerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: deal a fresh five-card hand. */
public class PokerDealPacket {

    private final BlockPos pos;

    public PokerDealPacket(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public static void encode(PokerDealPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static PokerDealPacket decode(FriendlyByteBuf buffer) {
        return new PokerDealPacket(buffer.readBlockPos());
    }

    public static void handle(PokerDealPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                PokerManager.deal(player, packet.pos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
