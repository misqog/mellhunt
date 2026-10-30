package ru.misqog.mellhunt.network;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL = "3";

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
        CHANNEL.messageBuilder(OpenSlotMachinePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenSlotMachinePacket::encode)
                .decoder(OpenSlotMachinePacket::decode)
                .consumerMainThread(OpenSlotMachinePacket::handle)
                .add();
        CHANNEL.messageBuilder(SlotMachineSpinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SlotMachineSpinPacket::encode)
                .decoder(SlotMachineSpinPacket::decode)
                .consumerMainThread(SlotMachineSpinPacket::handle)
                .add();
        CHANNEL.messageBuilder(SlotMachineResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlotMachineResultPacket::encode)
                .decoder(SlotMachineResultPacket::decode)
                .consumerMainThread(SlotMachineResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenRoulettePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenRoulettePacket::encode)
                .decoder(OpenRoulettePacket::decode)
                .consumerMainThread(OpenRoulettePacket::handle)
                .add();
        CHANNEL.messageBuilder(RouletteSpinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RouletteSpinPacket::encode)
                .decoder(RouletteSpinPacket::decode)
                .consumerMainThread(RouletteSpinPacket::handle)
                .add();
        CHANNEL.messageBuilder(RouletteResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RouletteResultPacket::encode)
                .decoder(RouletteResultPacket::decode)
                .consumerMainThread(RouletteResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenPokerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPokerPacket::encode)
                .decoder(OpenPokerPacket::decode)
                .consumerMainThread(OpenPokerPacket::handle)
                .add();
        CHANNEL.messageBuilder(PokerDealPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PokerDealPacket::encode)
                .decoder(PokerDealPacket::decode)
                .consumerMainThread(PokerDealPacket::handle)
                .add();
        CHANNEL.messageBuilder(PokerDrawPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PokerDrawPacket::encode)
                .decoder(PokerDrawPacket::decode)
                .consumerMainThread(PokerDrawPacket::handle)
                .add();
        CHANNEL.messageBuilder(PokerStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PokerStatePacket::encode)
                .decoder(PokerStatePacket::decode)
                .consumerMainThread(PokerStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenLaptopSlotsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenLaptopSlotsPacket::encode)
                .decoder(OpenLaptopSlotsPacket::decode)
                .consumerMainThread(OpenLaptopSlotsPacket::handle)
                .add();
        CHANNEL.messageBuilder(LaptopSpinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LaptopSpinPacket::encode)
                .decoder(LaptopSpinPacket::decode)
                .consumerMainThread(LaptopSpinPacket::handle)
                .add();
        CHANNEL.messageBuilder(LaptopResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LaptopResultPacket::encode)
                .decoder(LaptopResultPacket::decode)
                .consumerMainThread(LaptopResultPacket::handle)
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

    public static void sendOpenSlotMachine(ServerPlayer player, BlockPos pos, int winChance) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenSlotMachinePacket(pos, winChance));
    }

    public static void sendSlotSpin(BlockPos pos) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new SlotMachineSpinPacket(pos));
    }

    public static void sendSlotResult(ServerPlayer player, boolean won, int[] line, ItemStack prize) {
        ResourceLocation prizeId = prize.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getKey(prize.getItem());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SlotMachineResultPacket(won, line, prizeId, prize.getCount()));
    }

    public static void sendOpenRoulette(ServerPlayer player, BlockPos pos, int[] history) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenRoulettePacket(pos, history));
    }

    public static void sendRouletteSpin(BlockPos pos, int kind, int number, boolean turbo) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(),
                new RouletteSpinPacket(pos, kind, number, turbo));
    }

    public static void sendRouletteResult(ServerPlayer player, int winning, int kind, int number,
                                          boolean won, int payout, int error, int[] history) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RouletteResultPacket(winning, kind, number, won, payout, error, history));
    }

    public static void sendOpenPoker(ServerPlayer player, BlockPos pos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenPokerPacket(pos));
    }

    public static void sendPokerDeal(BlockPos pos) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new PokerDealPacket(pos));
    }

    public static void sendPokerDraw(BlockPos pos, int heldMask) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new PokerDrawPacket(pos, heldMask));
    }

    public static void sendPokerState(ServerPlayer player, PokerStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendOpenLaptopSlots(ServerPlayer player, BlockPos pos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenLaptopSlotsPacket(pos));
    }

    public static void sendLaptopSpin(BlockPos pos, int lines, boolean turbo) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(),
                new LaptopSpinPacket(pos, lines, turbo));
    }

    public static void sendLaptopResult(ServerPlayer player, int[] grid, int payout,
                                        int lineMask, int error) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new LaptopResultPacket(grid, payout, lineMask, error));
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
