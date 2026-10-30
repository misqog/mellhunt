package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: shared phase/timer/result state of one roulette table. */
public class RouletteStatePacket {

    private final BlockPos pos;
    private final int phase;
    private final int ticks;
    private final int winning;
    private final int[] history;

    public RouletteStatePacket(BlockPos pos, int phase, int ticks, int winning, int[] history) {
        this.pos = pos.immutable();
        this.phase = phase;
        this.ticks = ticks;
        this.winning = winning;
        this.history = history == null ? new int[0] : history.clone();
    }

    public static void encode(RouletteStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.phase);
        buffer.writeVarInt(packet.ticks);
        buffer.writeByte(packet.winning < 0 ? 255 : packet.winning);
        buffer.writeByte(packet.history.length);
        for (int number : packet.history) {
            buffer.writeByte(number);
        }
    }

    public static RouletteStatePacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int phase = buffer.readUnsignedByte();
        int ticks = buffer.readVarInt();
        int winning = buffer.readUnsignedByte();
        if (winning > 36) {
            winning = -1;
        }
        int[] history = new int[buffer.readUnsignedByte()];
        for (int i = 0; i < history.length; i++) {
            history[i] = buffer.readUnsignedByte();
        }
        return new RouletteStatePacket(pos, phase, ticks, winning, history);
    }

    public static void handle(RouletteStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.updateRouletteState(packet.pos, packet.phase,
                                packet.ticks, packet.winning, packet.history)));
        context.get().setPacketHandled(true);
    }
}
