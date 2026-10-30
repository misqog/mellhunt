package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the roulette result, payout and recent-number history. */
public class RouletteResultPacket {

    private final int winning;
    private final int kind;
    private final int number;
    private final boolean won;
    private final int payout;
    private final int error;
    private final int[] history;

    public RouletteResultPacket(int winning, int kind, int number,
                                boolean won, int payout, int error, int[] history) {
        this.winning = winning;
        this.kind = kind;
        this.number = number;
        this.won = won;
        this.payout = payout;
        this.error = error;
        this.history = history == null ? new int[0] : history.clone();
    }

    public static void encode(RouletteResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.winning);
        buffer.writeByte(packet.kind);
        buffer.writeByte(packet.number);
        buffer.writeBoolean(packet.won);
        buffer.writeVarInt(packet.payout);
        buffer.writeByte(packet.error);
        buffer.writeByte(packet.history.length);
        for (int number : packet.history) {
            buffer.writeByte(number);
        }
    }

    public static RouletteResultPacket decode(FriendlyByteBuf buffer) {
        int winning = buffer.readUnsignedByte();
        int kind = buffer.readUnsignedByte();
        int number = buffer.readUnsignedByte();
        boolean won = buffer.readBoolean();
        int payout = buffer.readVarInt();
        int error = buffer.readUnsignedByte();
        int[] history = new int[buffer.readUnsignedByte()];
        for (int i = 0; i < history.length; i++) {
            history[i] = buffer.readUnsignedByte();
        }
        return new RouletteResultPacket(winning, kind, number, won, payout, error, history);
    }

    public static void handle(RouletteResultPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.showRouletteResult(packet.winning, packet.kind,
                                packet.number, packet.won, packet.payout, packet.error,
                                packet.history)));
        context.get().setPacketHandled(true);
    }
}
