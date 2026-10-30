package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.PokerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: replace every card that is not marked as held. */
public class PokerDrawPacket {

    private final BlockPos pos;
    private final int heldMask;

    public PokerDrawPacket(BlockPos pos, int heldMask) {
        this.pos = pos.immutable();
        this.heldMask = heldMask & 31;
    }

    public static void encode(PokerDrawPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.heldMask);
    }

    public static PokerDrawPacket decode(FriendlyByteBuf buffer) {
        return new PokerDrawPacket(buffer.readBlockPos(), buffer.readUnsignedByte());
    }

    public static void handle(PokerDrawPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                PokerManager.draw(player, packet.pos, packet.heldMask);
            }
        });
        ctx.setPacketHandled(true);
    }
}
