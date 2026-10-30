package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the video-poker screen on a poker table. */
public class OpenPokerPacket {

    private final BlockPos pos;

    public OpenPokerPacket(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public static void encode(OpenPokerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
    }

    public static OpenPokerPacket decode(FriendlyByteBuf buffer) {
        return new OpenPokerPacket(buffer.readBlockPos());
    }

    public static void handle(OpenPokerPacket packet, Supplier<NetworkEvent.Context> context) {
        BlockPos pos = packet.pos;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.openPoker(pos)));
        context.get().setPacketHandled(true);
    }
}
