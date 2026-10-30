package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.casino.LaptopSlots;
import ru.misqog.mellhunt.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the final 5 x 3 grid, winning lines and payout. */
public class LaptopResultPacket {

    private final int[] grid;
    private final int payout;
    private final int lineMask;
    private final int error;

    public LaptopResultPacket(int[] grid, int payout, int lineMask, int error) {
        this.grid = new int[LaptopSlots.CELLS];
        if (grid != null) {
            System.arraycopy(grid, 0, this.grid, 0, Math.min(grid.length, this.grid.length));
        }
        this.payout = payout;
        this.lineMask = lineMask;
        this.error = error;
    }

    public static void encode(LaptopResultPacket packet, FriendlyByteBuf buffer) {
        for (int symbol : packet.grid) {
            buffer.writeByte(symbol);
        }
        buffer.writeVarInt(packet.payout);
        buffer.writeByte(packet.lineMask);
        buffer.writeByte(packet.error);
    }

    public static LaptopResultPacket decode(FriendlyByteBuf buffer) {
        int[] grid = new int[LaptopSlots.CELLS];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = buffer.readUnsignedByte();
        }
        return new LaptopResultPacket(grid, buffer.readVarInt(),
                buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }

    public static void handle(LaptopResultPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.showLaptopResult(packet.grid,
                                packet.payout, packet.lineMask, packet.error)));
        context.get().setPacketHandled(true);
    }
}
