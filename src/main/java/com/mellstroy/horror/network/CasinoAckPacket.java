package com.mellstroy.horror.network;

import com.mellstroy.horror.casino.CasinoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the casino sound has finished, apply the result now. */
public class CasinoAckPacket {

    public CasinoAckPacket() {
    }

    public static void encode(CasinoAckPacket packet, FriendlyByteBuf buffer) {
    }

    public static CasinoAckPacket decode(FriendlyByteBuf buffer) {
        return new CasinoAckPacket();
    }

    public static void handle(CasinoAckPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CasinoManager.handleAck(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
