package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the visible state of the current poker hand. */
public class PokerStatePacket {

    public static final int IDLE = 0;
    public static final int HOLD = 1;
    public static final int RESULT = 2;

    private final int phase;
    private final int[] cards;
    private final int heldMask;
    private final int payout;
    private final int category;
    private final int error;

    public PokerStatePacket(int phase, int[] cards, int heldMask, int payout, int category, int error) {
        this.phase = phase;
        this.cards = new int[5];
        if (cards != null) {
            System.arraycopy(cards, 0, this.cards, 0, Math.min(5, cards.length));
        }
        this.heldMask = heldMask & 31;
        this.payout = payout;
        this.category = category;
        this.error = error;
    }

    public static void encode(PokerStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.phase);
        for (int card : packet.cards) {
            buffer.writeByte(card);
        }
        buffer.writeByte(packet.heldMask);
        buffer.writeVarInt(packet.payout);
        buffer.writeByte(packet.category);
        buffer.writeByte(packet.error);
    }

    public static PokerStatePacket decode(FriendlyByteBuf buffer) {
        int phase = buffer.readUnsignedByte();
        int[] cards = new int[5];
        for (int i = 0; i < 5; i++) {
            cards[i] = buffer.readUnsignedByte();
        }
        int held = buffer.readUnsignedByte();
        int payout = buffer.readVarInt();
        int category = buffer.readUnsignedByte();
        int error = buffer.readUnsignedByte();
        return new PokerStatePacket(phase, cards, held, payout, category, error);
    }

    public static void handle(PokerStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.showPokerState(packet.phase, packet.cards,
                                packet.heldMask, packet.payout, packet.category, packet.error)));
        context.get().setPacketHandled(true);
    }
}
