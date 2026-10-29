package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: the slot machine result.
 *
 * <p>{@code prizeId} is an item registry name (empty when the spin lost) and
 * {@code prizeCount} is how many of it were put into the player's inventory.
 * The client only needs this to show the prize after the reels stop - the
 * prize itself is already granted on the server.</p>
 */
public class SlotMachineResultPacket {

    private final boolean won;
    private final int[] line;
    private final String prizeId;
    private final int prizeCount;

    public SlotMachineResultPacket(boolean won, int[] line, ResourceLocation prizeId, int prizeCount) {
        if (line == null || line.length != 3) {
            throw new IllegalArgumentException("A slot machine result needs exactly 3 symbols");
        }
        this.won = won;
        this.line = line.clone();
        this.prizeId = prizeId == null ? "" : prizeId.toString();
        this.prizeCount = prizeCount;
    }

    public boolean isWon() {
        return this.won;
    }

    public int[] getLine() {
        return this.line.clone();
    }

    public String getPrizeId() {
        return this.prizeId;
    }

    public int getPrizeCount() {
        return this.prizeCount;
    }

    public static void encode(SlotMachineResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.won);
        for (int symbol : packet.line) {
            buffer.writeByte(symbol);
        }
        buffer.writeUtf(packet.prizeId);
        buffer.writeVarInt(packet.prizeCount);
    }

    public static SlotMachineResultPacket decode(FriendlyByteBuf buffer) {
        boolean won = buffer.readBoolean();
        int[] line = {buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte()};
        String prizeId = buffer.readUtf();
        int prizeCount = buffer.readVarInt();
        return new SlotMachineResultPacket(won, line,
                prizeId.isEmpty() ? null : new ResourceLocation(prizeId), prizeCount);
    }

    public static void handle(SlotMachineResultPacket packet, Supplier<NetworkEvent.Context> context) {
        boolean won = packet.won;
        int[] line = packet.line;
        String prizeId = packet.prizeId;
        int prizeCount = packet.prizeCount;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.showSlotResult(won, line, prizeId, prizeCount)));
        context.get().setPacketHandled(true);
    }
}
