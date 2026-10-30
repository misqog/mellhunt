package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.RouletteManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: place a bet and spin the roulette wheel. */
public class RouletteSpinPacket {

    private final BlockPos pos;
    private final int kind;
    private final int number;
    private final boolean turbo;

    public RouletteSpinPacket(BlockPos pos, int kind, int number, boolean turbo) {
        this.pos = pos.immutable();
        this.kind = kind;
        this.number = number;
        this.turbo = turbo;
    }

    public static void encode(RouletteSpinPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.kind);
        buffer.writeByte(packet.number);
        buffer.writeBoolean(packet.turbo);
    }

    public static RouletteSpinPacket decode(FriendlyByteBuf buffer) {
        return new RouletteSpinPacket(buffer.readBlockPos(),
                buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readBoolean());
    }

    public static void handle(RouletteSpinPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                RouletteManager.spin(player, packet.pos, packet.kind, packet.number, packet.turbo);
            }
        });
        ctx.setPacketHandled(true);
    }
}
