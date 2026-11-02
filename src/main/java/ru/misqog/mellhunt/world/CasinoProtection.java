package ru.misqog.mellhunt.world;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The casino is a fixed set piece: players cannot break or place blocks there.
 *
 * <p>Only the dimension itself is locked, so the Overworld, the Nether, the
 * End and the house logic keep working exactly as before.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class CasinoProtection {

    /** X/Z bounds of the placed structure, with a one block margin. */
    private static final int MIN_X = -1;
    private static final int MAX_X = 38;
    private static final int MIN_Z = -1;
    private static final int MAX_Z = 28;
    private static final int MIN_Y = 63;
    private static final int MAX_Y = 72;

    private static boolean insideCasino(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (!MellstroyDimensions.isCasino(level)) {
            return false;
        }
        return pos.getX() >= MIN_X && pos.getX() <= MAX_X
                && pos.getZ() >= MIN_Z && pos.getZ() <= MAX_Z
                && pos.getY() >= MIN_Y && pos.getY() <= MAX_Y;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!insideCasino(event.getLevel(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        Player player = event.getPlayer();
        if (!player.isCreative()) {
            player.displayClientMessage(
                    Component.translatable("message.mellhunt.casino.locked")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!insideCasino(event.getLevel(), event.getPos())) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            if (player.isCreative()) {
                // Admins may still build while reviewing the room.
                return;
            }
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.translatable("message.mellhunt.casino.locked")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (insideCasino(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (insideCasino(event.getLevel(), event.getFaceOffsetPos())) {
            event.setCanceled(true);
        }
    }

    /** Explosions must not dig holes in the casino either. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!MellstroyDimensions.isCasino(event.getLevel())) {
            return;
        }
        // Blocks stay intact; nearby entities are still pushed by the blast.
        event.getAffectedBlocks().removeIf(pos -> insideCasino(event.getLevel(), pos));
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!insideCasino(event.getLevel(), event.getPos())) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!isOurFurniture(state)) {
            // Vanilla containers (chests, etc.) cannot be opened there.
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!insideCasino(event.getLevel(), event.getPos())) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isCreative()) {
            event.setCanceled(true);
        }
    }

    /** The mod's own casino furniture must keep reacting to clicks. */
    private static boolean isOurFurniture(BlockState state) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && MellstroyMod.MOD_ID.equals(id.getNamespace());
    }

    private CasinoProtection() {
    }
}
