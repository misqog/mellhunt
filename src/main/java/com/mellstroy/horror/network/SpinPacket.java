package com.mellstroy.horror.network;

import com.mellstroy.horror.casino.CasinoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed SPIN. */
public class SpinPacket {

    public SpinPacket() {
    }

    public static void encode(SpinPacket packet, FriendlyByteBuf buffer) {
    }

    public static SpinPacket decode(FriendlyByteBuf buffer) {
        return new SpinPacket();
    }

    public static void handle(SpinPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CasinoManager.handleSpin(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
