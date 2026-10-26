package ru.misqog.mellhunt.spawn;

import ru.misqog.mellhunt.entity.MellstroyEntity;
import ru.misqog.mellhunt.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Shared surface-spawn logic. The far respawn after the casino uses
 * {@code farFromAllPlayers = true}; normal natural spawning only guarantees
 * distance from its anchor player.
 */
public final class MellstroySpawner {

    public static boolean trySpawn(ServerLevel level, ServerPlayer anchor, int min, int max,
                                   boolean farFromAllPlayers, boolean announce, int graceTicks) {
        int minimum = Math.max(1, min);
        int maximum = Math.max(minimum + 1, max);

        for (int attempt = 0; attempt < 128; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = minimum + level.random.nextDouble() * (maximum - minimum);

            int x = (int) Math.floor(anchor.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(anchor.getZ() + Math.sin(angle) * distance);

            BlockPos surface = new BlockPos(x, level.getMinBuildHeight(), z);
            if (!level.isLoaded(surface)) {
                continue;
            }

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() + 2) {
                continue;
            }

            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();

            if (anchor.distanceToSqr(x + 0.5D, y, z + 0.5D) < (double) minimum * minimum) {
                continue;
            }
            if (farFromAllPlayers && isTooCloseToAnyPlayer(level, x, y, z, minimum)) {
                continue;
            }

            BlockState ground = level.getBlockState(below);
            if (!ground.isSolidRender(level, below)) {
                continue;
            }
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            if (!level.getFluidState(pos).isEmpty()) {
                continue;
            }

            MellstroyEntity mob = ModEntities.MELLSTROY.get().create(level);
            if (mob == null) {
                continue;
            }

            mob.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            if (!level.addFreshEntity(mob)) {
                continue;
            }

            if (graceTicks > 0) {
                mob.giveGrace(graceTicks);
            }
            if (announce) {
                anchor.displayClientMessage(
                        Component.translatable("message.mellhunt.arrived")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            }
            return true;
        }
        return false;
    }

    private static boolean isTooCloseToAnyPlayer(ServerLevel level, int x, int y, int z, int min) {
        double minSqr = (double) min * min;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x + 0.5D, y, z + 0.5D) < minSqr) {
                return true;
            }
        }
        return false;
    }

    private MellstroySpawner() {
    }
}
