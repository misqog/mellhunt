package com.mellstroy.horror.world;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.casino.CasinoManager;
import com.mellstroy.horror.entity.FatCatEntity;
import com.mellstroy.horror.entity.MellstroyEntity;
import com.mellstroy.horror.network.ModNetwork;
import com.mellstroy.horror.registry.ModEntities;
import com.mellstroy.horror.registry.ModSounds;
import com.mellstroy.horror.spawn.MellstroyRespawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent Overworld house placement and the house's one-shot horror events. */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID)
public final class MellstroyHouseManager {
    private static final String DATA_NAME = "mellstroy_houses";
    private static final int CELL_SIZE = 2000;
    /** Size of mellstroy_house.nbt (14 x 11 x 12). */
    private static final int HOUSE_WIDTH = 14;
    private static final int HOUSE_DEPTH = 12;
    /** Houses are never closer than this to each other. */
    private static final int MIN_HOUSE_SPACING = 1000;
    private static final int FIRST_MIN_DISTANCE = 50;
    private static final int FIRST_MAX_DISTANCE = 90;
    private static final int RUSH_START_DISTANCE = 100;
    /** Used to look for Mellstroy anywhere in a level. */
    private static final AABB EVERYWHERE =
            new AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 8192.0D, 3.0E7D);

    private MellstroyHouseManager() {}

    private static HouseData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HouseData::load, HouseData::new, DATA_NAME);
    }

    @SubscribeEvent
    public static void onOverworldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level != level.getServer().overworld() || level.getGameTime() % 20 != 0) {
            return;
        }
        HouseData data = data(level);
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        for (BlockPos house : data.houses) {
            if (data.seenHouses.contains(key(house))) continue;
            for (ServerPlayer player : players) {
                if (canSeeHouse(level, player, house)) {
                    data.seenHouses.add(key(house));
                    data.setDirty();
                    // Played in the player's ears: a positional sound would be
                    // inaudible at the distance the house is spotted from.
                    ModNetwork.sendPlayerSound(player, SoundEvents.WARDEN_ROAR, 1.1F, 0.65F);
                    break;
                }
            }
        }

        for (ServerPlayer player : players) {
            int cellX = data.houses.isEmpty() ? 0
                    : Math.floorDiv(player.blockPosition().getX() - data.anchorX + CELL_SIZE / 2, CELL_SIZE);
            int cellZ = data.houses.isEmpty() ? 0
                    : Math.floorDiv(player.blockPosition().getZ() - data.anchorZ + CELL_SIZE / 2, CELL_SIZE);
            String cell = cellX + "," + cellZ;
            if (data.cells.contains(cell)) continue;

            if (data.houses.isEmpty()) {
                if (placeFirstHouseNearPath(level, player, data)) {
                    data.cells.add("0,0");
                    data.setDirty();
                    return;
                }
            } else if (placeHouseInCell(level, cellX, cellZ, data)) {
                return;
            }
        }
    }

    private static boolean canSeeHouse(ServerLevel level, ServerPlayer player, BlockPos house) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(house.offset(7, 5, 6));
        Vec3 direction = center.subtract(eye);
        if (direction.lengthSqr() > 64.0D * 64.0D
                || player.getLookAngle().dot(direction.normalize()) < 0.88D) return false;

        // The centre of the house is behind its own walls, so a plain "did the
        // ray reach the centre" test would never pass. Instead the house is
        // considered seen when the first solid block the ray hits belongs to
        // the house (and not, for example, to a hill in front of it).
        HitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return isInsideHouse(house, BlockPos.containing(hit.getLocation()));
    }

    private static boolean isInsideHouse(BlockPos house, BlockPos pos) {
        // One block of tolerance: the ray hits the outer surface of the wall,
        // which can round to the neighbouring block.
        return pos.getX() >= house.getX() - 1 && pos.getX() <= house.getX() + 14
                && pos.getY() >= house.getY() - 1 && pos.getY() <= house.getY() + 11
                && pos.getZ() >= house.getZ() - 1 && pos.getZ() <= house.getZ() + 12;
    }

    private static boolean placeFirstHouseNearPath(ServerLevel level, ServerPlayer player, HouseData data) {
        float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        for (int attempt = 0; attempt < 100; attempt++) {
            double distance = FIRST_MIN_DISTANCE + level.random.nextDouble()
                    * (FIRST_MAX_DISTANCE - FIRST_MIN_DISTANCE);
            double side = (level.random.nextDouble() - 0.5D) * 70.0D;
            int x = (int) Math.floor(player.getX() + forwardX * distance - forwardZ * side);
            int z = (int) Math.floor(player.getZ() + forwardZ * distance + forwardX * side);
            Integer y = safeHouseY(level, x, z);
            if (y == null) continue;
            if (placeHouse(level, new BlockPos(x, y, z), data)) return true;
        }
        return false;
    }

    private static boolean placeHouseInCell(ServerLevel level, int cellX, int cellZ, HouseData data) {
        // Houses sit around the centre of their 2000x2000 cell. Keeping the
        // random offset within a quarter of the cell guarantees that two
        // houses are always at least 1000 blocks apart, even in neighbouring
        // cells.
        int centerX = data.anchorX + cellX * CELL_SIZE;
        int centerZ = data.anchorZ + cellZ * CELL_SIZE;
        int spread = CELL_SIZE / 4;
        for (int attempt = 0; attempt < 32; attempt++) {
            int x = centerX + level.random.nextInt(spread * 2 + 1) - spread;
            int z = centerZ + level.random.nextInt(spread * 2 + 1) - spread;
            Integer y = safeHouseY(level, x, z);
            if (y == null) continue;
            if (placeHouse(level, new BlockPos(x, y, z), data)) {
                data.cells.add(cellX + "," + cellZ);
                data.setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * Validates the whole 14x12 footprint plus a one-block skirt and returns
     * the Y at which the house should be placed, or {@code null} when the site
     * is unsuitable.
     *
     * <p>The checks reject water (oceans, rivers, lakes, shorelines and ice
     * over water), ravines/overhangs (a solid three-block foundation under
     * every floor tile) and slopes steeper than two blocks across the entire
     * footprint. The returned Y is one block below the lowest surface so the
     * house can never float above the terrain.</p>
     */
    @Nullable
    private static Integer safeHouseY(ServerLevel level, int x, int z) {
        BlockPos sample = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.hasChunkAt(sample)) return null;
        int centerY = terrainHeight(level, x, z);
        if (centerY < level.getSeaLevel() + 2) return null;

        int minY = Integer.MAX_VALUE;
        for (int dx = -1; dx <= HOUSE_WIDTH; dx++) {
            for (int dz = -1; dz <= HOUSE_DEPTH; dz++) {
                int cx = x + dx;
                int cz = z + dz;
                if (!level.hasChunkAt(new BlockPos(cx, level.getMinBuildHeight(), cz))) return null;

                int h = terrainHeight(level, cx, cz);
                if (h < level.getSeaLevel() + 2) return null;
                // A flat-ish site cannot be the wall of a ravine.
                if (Math.abs(h - centerY) > 2) return null;

                boolean footprint = dx >= 0 && dx < HOUSE_WIDTH && dz >= 0 && dz < HOUSE_DEPTH;
                if (footprint) {
                    minY = Math.min(minY, h);
                }

                BlockPos ground = new BlockPos(cx, h - 1, cz);
                if (!level.getBlockState(ground).isSolidRender(level, ground)) return null;

                // No water at the surface and none hidden directly under it
                // (this also rejects a thin crust of ice over an ocean).
                for (int below = 0; below <= 4; below++) {
                    BlockPos check = new BlockPos(cx, h - 1 - below, cz);
                    if (!level.getFluidState(check).isEmpty()) return null;
                    if (below < 3 && !level.getBlockState(check).isSolidRender(level, check)) {
                        // Requires a three-block solid foundation, which
                        // rules out overhangs above caves and ravines.
                        return null;
                    }
                }
            }
        }
        if (minY == Integer.MAX_VALUE) return null;

        for (BlockPos existing : data(level).houses) {
            double dx = existing.getX() - x;
            double dz = existing.getZ() - z;
            if (dx * dx + dz * dz < (double) MIN_HOUSE_SPACING * MIN_HOUSE_SPACING) return null;
        }
        return minY - 1;
    }

    /**
     * Surface height that ignores tree trunks. Without this, one tree in the
     * footprint would make a perfectly flat meadow look like a cliff and the
     * house would never spawn in forests.
     */
    private static int terrainHeight(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int minY = level.getMinBuildHeight();
        while (y - 1 > minY) {
            BlockPos below = new BlockPos(x, y - 1, z);
            BlockState state = level.getBlockState(below);
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                break;
            }
            y--;
        }
        return y;
    }

    private static boolean placeHouse(ServerLevel level, BlockPos origin, HouseData data) {
        StructureTemplate template = loadTemplate(level);
        if (template == null) return false;
        boolean placed = template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setIgnoreEntities(true), level.random, 2);
        if (!placed) return false;

        data.houses.add(origin.immutable());
        if (data.houses.size() == 1) {
            data.anchorX = origin.getX();
            data.anchorZ = origin.getZ();
        }
        data.cells.add(Math.floorDiv(origin.getX() - data.anchorX + CELL_SIZE / 2, CELL_SIZE) + ","
                + Math.floorDiv(origin.getZ() - data.anchorZ + CELL_SIZE / 2, CELL_SIZE));
        data.setDirty();

        // 25% of newly placed houses get two house cats and eight fish items.
        if (level.random.nextInt(100) < 25) {
            spawnHouseCatsAndFish(level, origin, template);
        }
        return true;
    }

    /**
     * Scatters two wild house cats and eight fish items on the ground floor of
     * the freshly placed house. The cats are marked so only they can trigger
     * the one-time first-sight sound.
     */
    private static void spawnHouseCatsAndFish(ServerLevel level, BlockPos origin, StructureTemplate template) {
        Vec3i size = template.getSize();
        List<BlockPos> spots = new ArrayList<>();

        for (int x = 1; x < size.getX() - 1; x++) {
            for (int z = 1; z < size.getZ() - 1; z++) {
                for (int y = 1; y < size.getY() - 1; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    BlockPos below = pos.below();
                    if (level.getBlockState(below).isSolidRender(level, below)
                            && level.getBlockState(pos.above()).isAir()) {
                        spots.add(pos.immutable());
                        break;
                    }
                }
            }
        }

        if (spots.isEmpty()) {
            return;
        }

        // Keep a copy so the requested 2 cats and 8 fish are always spawned,
        // even if the house has fewer free floor tiles than that.
        List<BlockPos> allSpots = new ArrayList<>(spots);

        for (int i = 0; i < 2; i++) {
            BlockPos spot = pickSpot(spots, allSpots, level);
            FatCatEntity cat = ModEntities.FAT_CAT.get().create(level);
            if (cat == null) {
                continue;
            }
            cat.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            cat.setHouseCat(true);
            cat.setPersistenceRequired();
            cat.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null, null);
            level.addFreshEntity(cat);
        }

        Item[] fish = {Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH};
        for (int i = 0; i < 8; i++) {
            BlockPos spot = pickSpot(spots, allSpots, level);
            Item item = fish[level.random.nextInt(fish.length)];
            ItemEntity entity = new ItemEntity(level,
                    spot.getX() + 0.5D, spot.getY() + 0.2D, spot.getZ() + 0.5D,
                    new ItemStack(item));
            entity.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.05D, 0.05D,
                    (level.random.nextDouble() - 0.5D) * 0.05D);
            entity.setPickUpDelay(10);
            // Keep the fish lying around instead of despawning after five minutes.
            entity.lifespan = Integer.MAX_VALUE;
            level.addFreshEntity(entity);
        }
    }

    private static BlockPos pickSpot(List<BlockPos> spots, List<BlockPos> allSpots, ServerLevel level) {
        if (!spots.isEmpty()) {
            return spots.remove(level.random.nextInt(spots.size()));
        }
        return allSpots.get(level.random.nextInt(allSpots.size()));
    }

    private static StructureTemplate loadTemplate(ServerLevel level) {
        ResourceLocation id = new ResourceLocation(MellstroyMod.MOD_ID, "mellstroy_house");
        return level.getServer().getStructureManager().get(id).orElse(null);
    }

    @SubscribeEvent
    public static void onHouseBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        HouseData data = data(level);
        BlockPos broken = event.getPos();
        BlockPos house = containingHouse(data, broken);
        if (house == null) return;

        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // Creative is for building and testing: breaking the house must not
        // summon Mellstroy. Survival, adventure and other modes are unchanged.
        if (player.isCreative()) return;

        // There is only one Mellstroy. Never summon him while he is already
        // busy with someone: chasing, rushing, searching a hidden player, or
        // sitting in the casino of the player he just caught. Otherwise the
        // new rush would replace him mid-chase and he would "instantly switch"
        // to whoever broke this block. Once he is free again (for example
        // after the kill), the next break can call him normally.
        if (isRushActive() || MellstroyEntity.isAnyOccupied(level.getServer())
                || CasinoManager.hasActiveSession()) return;

        if (!startHouseRush(level, house, player)) return;
        data.triggeredHouses.add(key(house));
        data.setDirty();
    }

    @SubscribeEvent
    public static void onReadHouseLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.LECTERN)
                || !isHousePosition(data(level), pos)
                || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)
                || !lectern.hasBook()) return;

        HouseData data = data(level);
        String bookKey = key(pos);
        if (!data.readBooks.add(bookKey)) return;
        data.setDirty();
        BlockPos door = openHouseDoor(level, containingHouse(data, pos));
        BlockPos soundOrigin = door == null ? pos : door;
        level.playSound(null, soundOrigin, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.85F);
        playBookStep(level, soundOrigin, player.getUUID(), 0);
    }

    private static final List<PendingBookSound> BOOK_SOUNDS = new ArrayList<>();
    private record PendingBookSound(ServerLevel level, BlockPos pos, UUID playerId, int step, int ticks) {}

    private static BlockPos openHouseDoor(ServerLevel level, BlockPos house) {
        if (house == null) return null;
        StructureTemplate template = loadTemplate(level);
        if (template == null) return null;
        var settings = new StructurePlaceSettings();
        List<StructureTemplate.StructureBlockInfo> doors = template.filterBlocks(BlockPos.ZERO, settings, Blocks.DARK_OAK_DOOR);
        BlockPos openedDoor = null;
        for (StructureTemplate.StructureBlockInfo door : doors) {
            if (door.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            BlockPos doorPos = house.offset(door.pos());
            BlockState doorState = level.getBlockState(doorPos);
            if (doorState.getBlock() instanceof DoorBlock block) {
                block.setOpen(null, level, doorState, doorPos, true);
                openedDoor = doorPos;
            }
        }
        return openedDoor;
    }

    private static boolean startHouseRush(ServerLevel level, BlockPos house, ServerPlayer breaker) {
        // The house rush is more important than the casino cooldown: he must
        // come even if the 3-7 minute respawn timer is still pending.
        MellstroyRespawnManager.cancel();

        MellstroyEntity mob = spawnRushMob(level, house, breaker);
        if (mob != null) {
            mob.playTrackedSound(ModSounds.UVIDEL.get(), SoundSource.NEUTRAL, 1.0F, 0.8F);
            ModNetwork.sendPlayerSound(breaker, SoundEvents.WARDEN_ROAR, 1.0F, 0.7F);
        }

        // The alarm starts even if the first spawn attempt failed: it keeps
        // trying and always reports the real distance once he is there.
        new HouseAlarm(level, house.immutable(), breaker.getUUID(),
                mob == null ? null : mob.getUUID()).start();
        return true;
    }

    @Nullable
    private static MellstroyEntity spawnRushMob(ServerLevel level, BlockPos house, ServerPlayer breaker) {
        BlockPos spawn = findRushSpawn(level, house, breaker);
        if (spawn == null) return null;

        MellstroyEntity mob = ModEntities.MELLSTROY.get().create(level);
        if (mob == null) return null;

        mob.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
        mob.giveGrace(0);
        mob.summonForHouse(breaker);
        if (!level.addFreshEntity(mob)) return null;
        return mob;
    }

    /**
     * Finds a surface spawn about {@link #RUSH_START_DISTANCE} blocks from the
     * house. A wide fallback ring is used when the ideal ring is blocked by
     * water, a cliff or unloaded chunks.
     */
    @Nullable
    private static BlockPos findRushSpawn(ServerLevel level, BlockPos house, ServerPlayer breaker) {
        BlockPos spawn = findRushSpawnRing(level, house, breaker, 94.0D, 106.0D, 240);
        if (spawn != null) return spawn;
        return findRushSpawnRing(level, house, breaker, 40.0D, 160.0D, 480);
    }

    @Nullable
    private static BlockPos findRushSpawnRing(ServerLevel level, BlockPos house, ServerPlayer breaker,
                                              double minDistance, double maxDistance, int attempts) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + level.random.nextDouble() * (maxDistance - minDistance);
            int x = house.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = house.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) continue;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isSolidRender(level, below)
                    || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                    || !level.getFluidState(pos).isEmpty()) continue;

            double playerDistance = breaker.distanceToSqr(x + 0.5D, y, z + 0.5D);
            if (playerDistance < 24.0D * 24.0D) continue;

            double score = Math.abs(y - house.getY()) * 2.0D
                    + Math.abs(distance - RUSH_START_DISTANCE) * 0.5D
                    + playerDistance * 0.0001D;
            if (score < bestScore) {
                best = pos;
                bestScore = score;
            }
        }
        return best;
    }

    private static BlockPos containingHouse(HouseData data, BlockPos pos) {
        for (BlockPos house : data.houses) {
            if (pos.getX() >= house.getX() && pos.getX() <= house.getX() + 13
                    && pos.getY() >= house.getY() && pos.getY() <= house.getY() + 10
                    && pos.getZ() >= house.getZ() && pos.getZ() <= house.getZ() + 11) return house;
        }
        return null;
    }

    private static boolean isHousePosition(HouseData data, BlockPos pos) {
        return containingHouse(data, pos) != null;
    }

    private static String key(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * The red action-bar warning. It no longer counts down a fake number: it
     * shows Mellstroy's real distance to the player every single tick, from the
     * moment the block is broken. If the tracked mob disappears it spawns him
     * again at the rush distance, even while the casino respawn timer is
     * pending. The alarm ends as soon as he catches the player, so it never
     * outlives the rush or restarts it after the casino.
     */
    private static final class HouseAlarm {
        private final ServerLevel level;
        private final BlockPos house;
        private final UUID playerId;
        @Nullable
        private UUID mobId;
        private int missingTicks;
        private int closeTicks;
        /** Chunk that is currently force-ticking for the rushing mob. */
        @Nullable
        private ChunkPos tickingChunk;

        private HouseAlarm(ServerLevel level, BlockPos house, UUID playerId, @Nullable UUID mobId) {
            this.level = level;
            this.house = house;
            this.playerId = playerId;
            this.mobId = mobId;
        }

        private void start() {
            TICKING_ALARMS.add(this);
        }

        private void tick() {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || player.serverLevel() != level || !player.isAlive()) {
                stop();
                return;
            }

            // He got the player: the casino (or death) takes over from here, so
            // the rush is over. Without this the alarm would keep shouting over
            // the "caught" message and, after a won casino removed him, would
            // summon a brand new rush out of nowhere.
            if (CasinoManager.isInCasino(player)) {
                stop();
                return;
            }

            MellstroyEntity mob = resolveMob();
            if (mob == null) {
                releaseTickingChunk();
                player.displayClientMessage(Component.literal("МЕЛЛСТРОЙ УЖЕ БЛИЗКО: ...")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                if (++missingTicks < 10) return;
                missingTicks = 0;
                mob = spawnRushMob(level, house, player);
                if (mob == null) return;
                mobId = mob.getUUID();
                mob.playTrackedSound(ModSounds.UVIDEL.get(), SoundSource.NEUTRAL, 1.0F, 0.8F);
            } else {
                missingTicks = 0;
            }

            mob.keepRushing(player);
            keepChunkTicking(mob);

            // Real distance, refreshed every tick: during a rush he covers
            // twenty blocks per second, so the number has to drop fast.
            int distance = (int) Math.round(Math.sqrt(mob.distanceToSqr(player)));
            player.displayClientMessage(Component.literal("МЕЛЛСТРОЙ УЖЕ БЛИЗКО: " + distance)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);

            if (distance <= 2) {
                if (++closeTicks >= 10) {
                    stop();
                }
            } else {
                closeTicks = 0;
            }
        }

        /** Removes this alarm for good and releases the forced chunk. */
        private void stop() {
            releaseTickingChunk();
            TICKING_ALARMS.remove(this);
        }

        /**
         * The rush starts a hundred blocks away, which is outside the player's
         * simulation distance on lower settings. A forced ticket keeps the
         * chunk he is in entity-ticking, so he never freezes in place there.
         */
        private void keepChunkTicking(MellstroyEntity mob) {
            ChunkPos current = mob.chunkPosition();
            if (current.equals(this.tickingChunk)) {
                return;
            }
            releaseTickingChunk();
            level.getChunkSource().addRegionTicket(TicketType.FORCED, current, 3, current);
            this.tickingChunk = current;
        }

        private void releaseTickingChunk() {
            if (this.tickingChunk != null) {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, this.tickingChunk, 3, this.tickingChunk);
                this.tickingChunk = null;
            }
        }

        @Nullable
        private MellstroyEntity resolveMob() {
            if (mobId != null && level.getEntity(mobId) instanceof MellstroyEntity mob && !mob.isRemoved()) {
                return mob;
            }
            // The tracked one is gone (killed, despawned or unloaded). Adopt a
            // loaded one if it exists, otherwise the caller respawns him.
            List<MellstroyEntity> loaded = level.getEntitiesOfClass(MellstroyEntity.class, EVERYWHERE);
            if (loaded.isEmpty()) return null;
            return loaded.get(0);
        }
    }

    private static final Set<HouseAlarm> TICKING_ALARMS = new HashSet<>();

    /** While a house rush is running natural spawning must not replace him. */
    public static boolean isRushActive() {
        return !TICKING_ALARMS.isEmpty();
    }

    /**
     * Ends the house rush for one player, for example because Mellstroy caught
     * them and the casino took over. The alarm must not outlive the catch:
     * otherwise it would overwrite the "caught" message and, once the casino
     * removes him, summon a fresh rush without the player breaking anything.
     */
    public static void stopRush(UUID playerId) {
        for (HouseAlarm alarm : List.copyOf(TICKING_ALARMS)) {
            if (alarm.playerId.equals(playerId)) {
                alarm.stop();
            }
        }
    }

    /**
     * Ends every house rush at once. Used when Mellstroy is made to vanish
     * (the chekushka): no alarm may summon a fresh copy right after him.
     */
    public static void stopAllRushes() {
        for (HouseAlarm alarm : List.copyOf(TICKING_ALARMS)) {
            alarm.stop();
        }
    }

    @SubscribeEvent
    public static void onHouseAlarmTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            for (HouseAlarm alarm : List.copyOf(TICKING_ALARMS)) alarm.tick();
        }
    }

    @SubscribeEvent
    public static void onBookSoundTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = BOOK_SOUNDS.size() - 1; i >= 0; i--) {
            PendingBookSound sound = BOOK_SOUNDS.get(i);
            if (sound.level().getServer() != event.getServer()) {
                BOOK_SOUNDS.remove(i);
                continue;
            }
            if (sound.ticks() > 1) {
                BOOK_SOUNDS.set(i, new PendingBookSound(sound.level(), sound.pos(), sound.playerId(), sound.step(), sound.ticks() - 1));
                continue;
            }
            BOOK_SOUNDS.remove(i);
            if (sound.step() >= 0) {
                playBookStep(sound.level(), sound.pos(), sound.playerId(), sound.step());
            } else {
                ServerPlayer reader = event.getServer().getPlayerList().getPlayer(sound.playerId());
                if (reader != null && reader.serverLevel() == sound.level()) {
                    // The jump-scare plays in the reader's ears so the four
                    // phantom steps lead straight into it, no matter where in
                    // the house the player is standing.
                    ModNetwork.sendPlayerSound(reader, ModSounds.ARTUR.get(), 1.0F, 1.0F);
                }
            }
        }
    }

    private static void playBookStep(ServerLevel level, BlockPos pos, UUID playerId, int step) {
        level.playSound(null, pos, SoundEvents.WOOD_STEP, SoundSource.PLAYERS, 0.75F,
                1.2F + step * 0.03F);
        if (step < 3) {
            BOOK_SOUNDS.add(new PendingBookSound(level, pos.immutable(), playerId, step + 1, 3));
        } else {
            BOOK_SOUNDS.add(new PendingBookSound(level, pos.immutable(), playerId, -1, 1));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (HouseAlarm alarm : List.copyOf(TICKING_ALARMS)) {
            alarm.releaseTickingChunk();
        }
        TICKING_ALARMS.clear();
        BOOK_SOUNDS.clear();
    }

    private static final class HouseData extends SavedData {
        private int anchorX;
        private int anchorZ;
        private final Set<String> cells = new HashSet<>();
        private final Set<BlockPos> houses = new HashSet<>();
        private final Set<String> triggeredHouses = new HashSet<>();
        private final Set<String> readBooks = new HashSet<>();
        private final Set<String> seenHouses = new HashSet<>();

        private static HouseData load(CompoundTag tag) {
            HouseData data = new HouseData();
            data.anchorX = tag.getInt("AnchorX");
            data.anchorZ = tag.getInt("AnchorZ");
            readStringSet(tag, "Cells", data.cells);
            for (long pos : tag.getLongArray("Houses")) data.houses.add(BlockPos.of(pos));
            readStringSet(tag, "Triggered", data.triggeredHouses);
            readStringSet(tag, "ReadBooks", data.readBooks);
            readStringSet(tag, "SeenHouses", data.seenHouses);
            return data;
        }

        private static void readStringSet(CompoundTag tag, String name, Set<String> target) {
            net.minecraft.nbt.ListTag list = tag.getList(name, net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) target.add(list.getString(i));
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("AnchorX", anchorX);
            tag.putInt("AnchorZ", anchorZ);
            tag.put("Cells", stringList(cells));
            long[] positions = houses.stream().mapToLong(BlockPos::asLong).toArray();
            tag.putLongArray("Houses", positions);
            tag.put("Triggered", stringList(triggeredHouses));
            tag.put("ReadBooks", stringList(readBooks));
            tag.put("SeenHouses", stringList(seenHouses));
            return tag;
        }

        private static net.minecraft.nbt.ListTag stringList(Set<String> values) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (String value : values) list.add(net.minecraft.nbt.StringTag.valueOf(value));
            return list;
        }
    }
}
