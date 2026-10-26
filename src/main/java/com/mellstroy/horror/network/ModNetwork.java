package com.mellstroy.horror.network;

import com.mellstroy.horror.MellstroyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MellstroyMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenCasinoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenCasinoPacket::encode)
                .decoder(OpenCasinoPacket::decode)
                .consumerMainThread(OpenCasinoPacket::handle)
                .add();
        CHANNEL.messageBuilder(CasinoResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CasinoResultPacket::encode)
                .decoder(CasinoResultPacket::decode)
                .consumerMainThread(CasinoResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpinPacket::encode)
                .decoder(SpinPacket::decode)
                .consumerMainThread(SpinPacket::handle)
                .add();
        CHANNEL.messageBuilder(CasinoAckPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CasinoAckPacket::encode)
                .decoder(CasinoAckPacket::decode)
                .consumerMainThread(CasinoAckPacket::handle)
                .add();
        CHANNEL.messageBuilder(TrackedSoundPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TrackedSoundPacket::encode)
                .decoder(TrackedSoundPacket::decode)
                .consumerMainThread(TrackedSoundPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerSoundPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerSoundPacket::encode)
                .decoder(PlayerSoundPacket::decode)
                .consumerMainThread(PlayerSoundPacket::handle)
                .add();
    }

    public static void sendOpenCasino(ServerPlayer player, int winChance) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenCasinoPacket(winChance));
    }

    public static void sendResult(ServerPlayer player, boolean won, int[] line,
                                  boolean bonusRound, boolean secondChance) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CasinoResultPacket(won, bonusRound, secondChance, line));
    }

    public static void sendSpin() {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new SpinPacket());
    }

    public static void sendAck() {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new CasinoAckPacket());
    }

    /**
     * Tells nearby clients to play a sound at this entity. The client keeps
     * updating the sound position until the sound ends, so it truly follows
     * the mob rather than staying at the point where it started.
     */
    public static void sendTrackedSound(ServerLevel level, Entity entity, SoundEvent sound,
                                        SoundSource source, float volume, float pitch) {
        if (level == null || entity == null || sound == null) {
            return;
        }

        TrackedSoundPacket packet = new TrackedSoundPacket(entity.getId(), sound.getLocation(),
                source, volume, pitch);
        double rangeSqr = 96.0D * 96.0D;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) <= rangeSqr) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    /**
     * Plays a sound in one player's ears, independent of any world position.
     * Used for the "creepy noise" so it does not stay behind when the player
     * moves away from the spot where the sound started.
     */
    public static void sendPlayerSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerSoundPacket(sound.getLocation(), volume, pitch));
    }

    private ModNetwork() {
    }
}
