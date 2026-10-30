package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the laptop slot machine screen. */
public class OpenLaptopSlotsPacket {

    private final BlockPos pos;

    public OpenLaptopSlotsPacket(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public static void encode(OpenLaptopSlotsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static OpenLaptopSlotsPacket decode(FriendlyByteBuf buffer) {
        return new OpenLaptopSlotsPacket(buffer.readBlockPos());
    }

    public static void handle(OpenLaptopSlotsPacket packet, Supplier<NetworkEvent.Context> context) {
        BlockPos pos = packet.pos;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.openLaptopSlots(pos)));
        context.get().setPacketHandled(true);
    }
}
