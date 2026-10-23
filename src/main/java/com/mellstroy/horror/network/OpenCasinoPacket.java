package com.mellstroy.horror.network;

import com.mellstroy.horror.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the casino screen with the current jackpot chance. */
public class OpenCasinoPacket {

    private final int winChance;

    public OpenCasinoPacket(int winChance) {
        this.winChance = winChance;
    }

    public int getWinChance() {
        return this.winChance;
    }

    public static void encode(OpenCasinoPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.winChance);
    }

    public static OpenCasinoPacket decode(FriendlyByteBuf buffer) {
        return new OpenCasinoPacket(buffer.readVarInt());
    }

    public static void handle(OpenCasinoPacket packet, Supplier<NetworkEvent.Context> context) {
        int chance = packet.winChance;
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openCasino(chance)));
        context.get().setPacketHandled(true);
    }
}
