package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.LaptopSlotManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: spin the laptop's slot reels with a line count and speed. */
public class LaptopSpinPacket {

    private final BlockPos pos;
    private final int lines;
    private final boolean turbo;

    public LaptopSpinPacket(BlockPos pos, int lines, boolean turbo) {
        this.pos = pos.immutable();
        this.lines = lines;
        this.turbo = turbo;
    }

    public static void encode(LaptopSpinPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.lines);
        buffer.writeBoolean(packet.turbo);
    }

    public static LaptopSpinPacket decode(FriendlyByteBuf buffer) {
        return new LaptopSpinPacket(buffer.readBlockPos(),
                buffer.readUnsignedByte(), buffer.readBoolean());
    }

    public static void handle(LaptopSpinPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                LaptopSlotManager.spin(player, packet.pos, packet.lines, packet.turbo);
            }
        });
        ctx.setPacketHandled(true);
    }
}
