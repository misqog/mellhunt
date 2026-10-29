package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the playable slot machine screen. */
public class OpenSlotMachinePacket {

    private final BlockPos pos;
    private final int winChance;

    public OpenSlotMachinePacket(BlockPos pos, int winChance) {
        this.pos = pos.immutable();
        this.winChance = winChance;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getWinChance() {
        return this.winChance;
    }

    public static void encode(OpenSlotMachinePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.winChance);
    }

    public static OpenSlotMachinePacket decode(FriendlyByteBuf buffer) {
        return new OpenSlotMachinePacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(OpenSlotMachinePacket packet, Supplier<NetworkEvent.Context> context) {
        BlockPos pos = packet.pos;
        int chance = packet.winChance;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.openSlotMachine(pos, chance)));
        context.get().setPacketHandled(true);
    }
}
