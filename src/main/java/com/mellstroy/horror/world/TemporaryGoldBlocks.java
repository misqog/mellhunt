package com.mellstroy.horror.world;

import com.mellstroy.horror.MellstroyMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Gold blocks that Mellstroy builds when normal pathfinding fails.
 *
 * They are only a last-resort bridge/climb, live for a short time and cannot
 * be broken by players while active. When the mob dies or disappears, its
 * remaining blocks are removed immediately.
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class TemporaryGoldBlocks {

    private static final int LIFETIME_TICKS = 30 * 20;
    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> BLOCKS = new HashMap<>();

    private record Entry(UUID owner, long expiresAt) {
    }

    public static boolean place(ServerLevel level, BlockPos pos, UUID owner) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.canBeReplaced()) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        if (!level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState())) {
            return false;
        }

        Map<BlockPos, Entry> levelBlocks = BLOCKS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        levelBlocks.put(pos.immutable(), new Entry(owner, level.getGameTime() + LIFETIME_TICKS));
        return true;
    }

    /**
     * Rush variant: places gold over air or a fluid (including lava) with no
     * neighbour updates. Carving or bridging next to a Nether lava sea must
     * not schedule thousands of fluid ticks, which used to freeze the server.
     */
    public static boolean placeForced(ServerLevel level, BlockPos pos, UUID owner) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        BlockState current = level.getBlockState(pos);
        boolean replaceable = current.isAir()
                || current.canBeReplaced()
                || !current.getFluidState().isEmpty();
        if (!replaceable) {
            return false;
        }

        if (!level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2)) {
            return false;
        }

        Map<BlockPos, Entry> levelBlocks = BLOCKS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        levelBlocks.put(pos.immutable(), new Entry(owner, level.getGameTime() + LIFETIME_TICKS));
        return true;
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Entry> levelBlocks = BLOCKS.get(level.dimension());
        return levelBlocks != null
                && levelBlocks.containsKey(pos)
                && level.getBlockState(pos).is(Blocks.GOLD_BLOCK);
    }

    /** Removes every block that belongs to this mob, e.g. when it is discarded. */
    public static void clearMob(ServerLevel level, UUID owner) {
        Map<BlockPos, Entry> levelBlocks = BLOCKS.get(level.dimension());
        if (levelBlocks == null) {
            return;
        }

        Iterator<Map.Entry<BlockPos, Entry>> iterator = levelBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Entry> entry = iterator.next();
            if (entry.getValue().owner().equals(owner)) {
                BlockPos pos = entry.getKey();
                if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                    // Quiet removal: no cascade of neighbour/fluid updates.
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                iterator.remove();
            }
        }
        if (levelBlocks.isEmpty()) {
            BLOCKS.remove(level.dimension());
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, Entry> levelBlocks = BLOCKS.get(level.dimension());
            if (levelBlocks == null) {
                continue;
            }

            Iterator<Map.Entry<BlockPos, Entry>> iterator = levelBlocks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Entry> entry = iterator.next();
                if (now >= entry.getValue().expiresAt()) {
                    BlockPos pos = entry.getKey();
                    if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                        // Quiet removal: no cascade of neighbour/fluid updates.
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                    iterator.remove();
                }
            }
            if (levelBlocks.isEmpty()) {
                BLOCKS.remove(level.dimension());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
            tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
        }
    }

    private TemporaryGoldBlocks() {
    }
}
