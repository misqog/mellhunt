package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.SlotMachineManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed SPIN on a slot machine block. */
public class SlotMachineSpinPacket {

    private final BlockPos pos;

    public SlotMachineSpinPacket(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public static void encode(SlotMachineSpinPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static SlotMachineSpinPacket decode(FriendlyByteBuf buffer) {
        return new SlotMachineSpinPacket(buffer.readBlockPos());
    }

    public static void handle(SlotMachineSpinPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        BlockPos pos = packet.pos;
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SlotMachineManager.spin(player, pos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
