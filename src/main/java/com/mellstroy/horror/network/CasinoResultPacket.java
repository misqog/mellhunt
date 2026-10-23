package com.mellstroy.horror.network;

import com.mellstroy.horror.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the reels stopped on this result line. */
public class CasinoResultPacket {

    private final boolean won;
    private final int[] line;

    public CasinoResultPacket(boolean won, int[] line) {
        if (line == null || line.length != 3) {
            throw new IllegalArgumentException("A casino result line needs exactly 3 symbols");
        }
        this.won = won;
        this.line = line.clone();
    }

    public boolean isWon() {
        return this.won;
    }

    public int[] getLine() {
        return this.line.clone();
    }

    public static void encode(CasinoResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.won);
        for (int symbol : packet.line) {
            buffer.writeByte(symbol);
        }
    }

    public static CasinoResultPacket decode(FriendlyByteBuf buffer) {
        boolean won = buffer.readBoolean();
        int[] line = {
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte()
        };
        return new CasinoResultPacket(won, line);
    }

    public static void handle(CasinoResultPacket packet, Supplier<NetworkEvent.Context> context) {
        boolean won = packet.won;
        int[] line = packet.line;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.showResult(won, line)));
        context.get().setPacketHandled(true);
    }
}
