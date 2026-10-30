package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the roulette table screen with its recent numbers. */
public class OpenRoulettePacket {

    private final BlockPos pos;
    private final int[] history;

    public OpenRoulettePacket(BlockPos pos, int[] history) {
        this.pos = pos.immutable();
        this.history = history == null ? new int[0] : history.clone();
    }

    public static void encode(OpenRoulettePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.history.length);
        for (int number : packet.history) {
            buffer.writeByte(number);
        }
    }

    public static OpenRoulettePacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int[] history = new int[buffer.readUnsignedByte()];
        for (int i = 0; i < history.length; i++) {
            history[i] = buffer.readUnsignedByte();
        }
        return new OpenRoulettePacket(pos, history);
    }

    public static void handle(OpenRoulettePacket packet, Supplier<NetworkEvent.Context> context) {
        BlockPos pos = packet.pos;
        int[] history = packet.history;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.openRoulette(pos, history)));
        context.get().setPacketHandled(true);
    }
}
