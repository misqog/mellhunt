package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.RouletteManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: place or change the player's bet for the current round. */
public class RouletteSpinPacket {

    private final BlockPos pos;
    private final int kind;
    private final int number;

    public RouletteSpinPacket(BlockPos pos, int kind, int number) {
        this.pos = pos.immutable();
        this.kind = kind;
        this.number = number;
    }

    public static void encode(RouletteSpinPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.kind);
        buffer.writeByte(packet.number);
    }

    public static RouletteSpinPacket decode(FriendlyByteBuf buffer) {
        return new RouletteSpinPacket(buffer.readBlockPos(),
                buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }

    public static void handle(RouletteSpinPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                RouletteManager.bet(player, packet.pos, packet.kind, packet.number);
            }
        });
        ctx.setPacketHandled(true);
    }
}
