package com.mellstroy.horror.entity;

import com.mellstroy.horror.config.MellstroyConfig;
import com.mellstroy.horror.network.ModNetwork;
import com.mellstroy.horror.registry.ModSounds;
import com.mellstroy.horror.world.TemporaryGoldBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Mellstroy.
 *
 * He cannot be killed, never despawns and always knows roughly where you are.
 * But he is blind in one very specific way: if you break his line of sight,
 * crouch and stop moving, he loses your trail for a while.
 *
 * Every sound he makes is played from his body, so it gets quieter with distance.
 */
public class MellstroyEntity extends Monster {

    private static final EntityDataAccessor<Boolean> DATA_HUNTING =
            SynchedEntityData.defineId(MellstroyEntity.class, EntityDataSerializers.BOOLEAN);

    /** Used to find the single Mellstroy anywhere in any loaded chunk. */
    private static final AABB EVERYWHERE =
            new AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 8192.0D, 3.0E7D);

    /**
     * UUID of the one Mellstroy that is allowed to exist right now. A freshly
     * spawned Mellstroy always wins and deletes the previous one; a copy that
     * is merely loaded from disk is deleted when a newer one is active.
     */
    @Nullable
    private static UUID activeId;

    /** Ticks the player has to stay hidden before he loses them. */
    private static final int HIDE_TICKS = 40;
    /** A hidden player may be glimpsed through cover for this long without wasting the hide. */
    private static final int HIDE_VISIBLE_GRACE = 6;
    /** How long he searches after losing a player. */
    private static final int HIDE_COOLDOWN_TICKS = 30 * 20;
    /** How long he keeps walking towards the last known position. */
    private static final int SEARCH_TICKS = 10 * 20;
    /**
     * If the player gets this far away and stays out of sight for a few
     * seconds, he has genuinely escaped instead of just stepping behind a tree.
     */
    private static final double ESCAPE_DISTANCE = 64.0D;
    private static final int ESCAPE_TICKS = 4 * 20;
    /** "Ne dognal" is played this long after he loses you. */
    private static final int LOST_SOUND_DELAY = 7 * 20;
    /** After a casino win he ignores the player for a while. */
    public static final int GRACE_TICKS = 30 * 20;

    /** He is teleported this many blocks above the water he was stuck in. */
    private static final int WATER_ESCAPE_CLEARANCE = 4;
    /** He never lands closer than this to the player after a water escape. */
    private static final double WATER_ESCAPE_PLAYER_GAP = 24.0D;
    /** A mob that stands still this long starts breaking through walls. */
    private static final int STUCK_BREAK_TICKS = 3 * 20;

    private int lostTicks;
    private int hideCooldown;
    private int hideVisibleTicks;
    private int escapeTicks;
    private int searchTicks;
    @Nullable
    private Vec3 hideAnchor;
    private int graceTicks;
    private int nextPhraseTick = 60;
    private int lostSoundTick = -1;
    @Nullable
    private Vec3 lastKnownPos;

    /* Last-resort mobility when normal pathfinding cannot reach a player. */
    private static final int GOLD_BUDGET_REFRESH_TICKS = 30 * 20;
    private static final int GOLD_BUDGET = 24;
    private int pathStuckTicks;
    private int mobilityCooldown;
    private int breakCooldown;
    private int breaksLeft = 4;
    private int goldBudget = GOLD_BUDGET;
    private int goldResetTick;
    /** While positive he keeps building straight up and refuses to walk off the column. */
    private int climbTicks;
    private int stuckTicks;
    private int houseRushTicks;
    private int waterEscapeCooldown;
    @Nullable
    private UUID rushTargetId;
    private double previousStuckX = Double.NaN;
    private double previousStuckZ = Double.NaN;

    /** A chase that lasts this long makes him abandon pathfinding completely. */
    private static final int DESPERATE_TICKS = 60 * 20;
    /**
     * Blocks per tick while rushing. Twenty blocks per second is faster than
     * anything a player can do, and it makes a hundred block run take five
     * seconds.
     */
    private static final double RUSH_STEP = 1.0D;
    /**
     * A real player needs four ticks per block when pillaring up. He never
     * builds faster than that, so the climb looks like actual building.
     */
    private static final int PILLAR_TICKS = 4;
    /**
     * A staircase step (one block down + one block across) takes two ticks,
     * so he descends at ten blocks per second instead of materialising on top
     * of the player in a single tick.
     */
    private static final int STAIR_TICKS = 2;
    /** How long he has been chasing the current target without reaching them. */
    private int chaseTicks;
    private boolean desperate;
    @Nullable
    private UUID chaseTargetId;
    /**
     * seks.ogg is about 2.42 seconds long. The rush starts only after the
     * warning has finished, so the player hears it before Mellstroy speeds up.
     */
    private static final int SEKS_RUSH_DELAY_TICKS = 50;
    private boolean seksRushPending;
    private int seksRushAtTick;
    /** Ticks left before the next gold block of a pillar may be placed. */
    private int pillarCooldown;
    /** Ticks left before the next staircase step may be taken. */
    private int stairCooldown;
    /** Cooldown between random walks while there is nobody to hunt. */
    private int idleStrollCooldown;

    public MellstroyEntity(EntityType<? extends MellstroyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_OPEN, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.TRAPDOOR, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.LEAVES, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_OTHER, 8.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1.0F);
        this.goldResetTick = this.tickCount;
        this.setCustomName(Component.translatable("entity.mellstroy.mellstroy"));
        this.setCustomNameVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ATTACK_DAMAGE, 512.0D)
                .add(Attributes.FOLLOW_RANGE, 160.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 30.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 20.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_HUNTING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true) {
            @Override
            protected double getAttackReachSqr(LivingEntity target) {
                // He does not need to touch you to grab you.
                return 4.0D;
            }
        });
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new MellstroyNavigation(this, level);
    }

    /* ------------------------------------------------------------------ */
    /*  AI                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Safety net: a NaN position (for example from a division by zero in the
     * rush) makes chunk and entity lookups loop forever and freezes the whole
     * server. Such an entity is dropped instead of poisoning every tick.
     */
    @Override
    public void tick() {
        if (!Double.isFinite(this.getX()) || !Double.isFinite(this.getY())
                || !Double.isFinite(this.getZ())) {
            this.discard();
            return;
        }
        super.tick();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.level().isClientSide) {
            return;
        }

        // Frozen while the casino screen is open.
        if (this.isNoAi()) {
            return;
        }

        if (this.houseRushTicks > 0 && --this.houseRushTicks == 0) {
            this.setMovementSpeed(0.36D);
            this.rushTargetId = null;
            // The rush itself is over, but if he still has the player in sight
            // he keeps smashing through until he reaches them.
            if (this.getTarget() instanceof Player stillHere && isValidTarget(stillHere)) {
                this.desperate = true;
            }
        }

        this.setCustomNameVisible(false);
        if (!this.isRushing()) {
            this.tickBreakThroughObstacles();
        }
        if (!this.isRushing() && this.waterEscapeCooldown <= 0 && this.needsWaterEscape()) {
            this.waterEscapeCooldown = 20;
            this.escapeWaterToPlayerHeight();
        }

        if (this.hideCooldown > 0) {
            this.hideCooldown--;
        }
        if (this.searchTicks > 0) {
            this.searchTicks--;
        }
        if (this.graceTicks > 0) {
            this.graceTicks--;
        }
        if (this.mobilityCooldown > 0) {
            this.mobilityCooldown--;
        }
        if (this.breakCooldown > 0) {
            this.breakCooldown--;
        }
        if (this.waterEscapeCooldown > 0) this.waterEscapeCooldown--;
        if (this.tickCount - this.goldResetTick >= GOLD_BUDGET_REFRESH_TICKS) {
            this.goldResetTick = this.tickCount;
            this.goldBudget = GOLD_BUDGET;
            this.breaksLeft = 4;
        }

        // "Didn't catch you" is played exactly 7 seconds after he lost the player.
        if (this.lostSoundTick >= 0 && this.tickCount >= this.lostSoundTick) {
            this.lostSoundTick = -1;
            if (this.getTarget() == null) {
                this.playTrackedSound(ModSounds.NE_DOGNAL.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }

        Player target = this.getTarget() instanceof Player player ? player : null;
        if (target != null && !isValidTarget(target)) {
            this.setTarget(null);
            target = null;
        }

        if (target == null && this.graceTicks <= 0) {
            Player candidate = findNearestTarget();
            if (candidate != null && canAcquire(candidate)) {
                this.setTarget(candidate);
                target = candidate;
                onSpotted(candidate);
            }
        }

        if (target != null) {
            // Hiding works in every chase, including the house rush: sneak,
            // break line of sight and stand still for two seconds.
            boolean hiddenNow = isHidden(target);

            if (hiddenNow) {
                // While you are hidden he must NOT keep following your real
                // position, otherwise he simply runs around the tree and sees
                // you. He keeps the place where he saw you last instead.
                this.lostTicks++;
                this.hideVisibleTicks = 0;
                if (this.lostTicks >= HIDE_TICKS) {
                    loseTarget(target);
                    target = null;
                }
            } else {
                // He can still track you normally while you run - even behind
                // a tree - so breaking line of sight alone is not enough.
                this.lastKnownPos = target.position();

                if (this.lostTicks > 0 && target.isShiftKeyDown()
                        && this.getSensing().hasLineOfSight(target)) {
                    // He circled the tree and caught a glimpse. A few visible
                    // ticks must not throw away a successful hide.
                    if (++this.hideVisibleTicks >= HIDE_VISIBLE_GRACE) {
                        this.lostTicks = 0;
                        this.hideVisibleTicks = 0;
                    }
                } else {
                    this.lostTicks = 0;
                    this.hideVisibleTicks = 0;
                }
            }

            // Merely stepping behind a tree is not an escape: the player has
            // to actually get far away and stay out of sight for a while.
            if (target != null && this.houseRushTicks <= 0) {
                if (this.distanceToSqr(target) > ESCAPE_DISTANCE * ESCAPE_DISTANCE
                        && !this.getSensing().hasLineOfSight(target)) {
                    if (++this.escapeTicks >= ESCAPE_TICKS) {
                        loseTarget(target);
                        target = null;
                    }
                } else {
                    this.escapeTicks = 0;
                }
            }
        } else {
            this.pathStuckTicks = 0;
            this.lostTicks = 0;
            this.hideVisibleTicks = 0;
            this.escapeTicks = 0;
            this.seksRushPending = false;
            if (this.houseRushTicks <= 0) {
                this.desperate = false;
                this.chaseTicks = 0;
                this.chaseTargetId = null;
            }
        }

        boolean hunting = target != null && isValidTarget(target);
        if (this.entityData.get(DATA_HUNTING) != hunting) {
            this.entityData.set(DATA_HUNTING, hunting);
        }

        tickPhrases(hunting);

        if (hunting && target != null) {
            this.applyFear(target);
            this.tickChaseTimer(target);
            if (this.lostTicks > 0 && target.isShiftKeyDown()) {
                // You are hiding: stop chasing and scan the last known spot.
                // Without this pause a rushing Mellstroy covers the whole
                // hiding place before the two seconds are over.
                this.getNavigation().stop();
                this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
                this.setDeltaMovement(Vec3.ZERO);
                this.setSpeed(0.0F);
                this.setXxa(0.0F);
                this.setYya(0.0F);
                this.setZza(0.0F);
                this.setOnGround(true);
                if (this.lastKnownPos != null) {
                    this.getLookControl().setLookAt(
                            this.lastKnownPos.x, this.lastKnownPos.y + 1.5D, this.lastKnownPos.z,
                            30.0F, 30.0F);
                }
            } else if (this.isRushing()) {
                // Only kill normal pathfinding while he is still far away;
                // within three blocks the melee goal must be allowed to hit.
                if (this.distanceToSqr(target) > 9.0D) {
                    this.getNavigation().stop();
                    // The goal selector runs before this method, so erase any
                    // movement it queued: the rush itself moves him, nothing
                    // else may drag him off course.
                    this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
                    this.setSpeed(0.0F);
                    this.setXxa(0.0F);
                    this.setYya(0.0F);
                    this.setZza(0.0F);
                }
                this.rushTowards(target);
            } else if (this.climbTicks > 0) {
                this.tickClimb(target);
            } else {
                this.tickMobility(target);
            }
        } else {
            this.climbTicks = 0;
            if (this.searchTicks > 0 && this.lastKnownPos != null
                    && this.getNavigation().isDone() && this.tickCount % 20 == 0) {
                // Walk over to the place he last saw you. Then wander.
                this.getNavigation().moveTo(this.lastKnownPos.x, this.lastKnownPos.y, this.lastKnownPos.z, 0.8D);
            } else if (this.searchTicks <= 0) {
                // Nobody to hunt: he lives his own life and just wanders.
                this.tickIdle();
            }
        }

        // Do not let vanilla movement carry him off the column while he is
        // building upward. The move control is reset after navigation has
        // already run this tick, so there is no movement left to apply.
        if (this.climbTicks > 0) {
            this.stopForClimb();
        }
    }

    /**
     * Random phrases while chasing. The next phrase can only start after the
     * previous one has fully finished, plus a random 10-15 second pause.
     */
    private void tickPhrases(boolean hunting) {
        // Let seks.ogg finish before any random chase phrase can interrupt it.
        if (!hunting || this.seksRushPending || this.tickCount < this.nextPhraseTick) {
            return;
        }

        int index = this.random.nextInt(ModSounds.PHRASE_DURATIONS.length);
        float duration = ModSounds.PHRASE_DURATIONS[index];
        this.playTrackedSound(ModSounds.PHRASES.get(index).get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        this.nextPhraseTick = this.tickCount + (int) Math.ceil(duration * 20.0F) + 200 + this.random.nextInt(101);
    }

    /**
     * Sends the sound to nearby clients, which then keep the sound pinned to
     * this entity for its whole duration.
     */
    public void playTrackedSound(SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (this.level() instanceof ServerLevel level) {
            ModNetwork.sendTrackedSound(level, this, sound, source, volume, pitch);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Last-resort movement                                              */
    /* ------------------------------------------------------------------ */

    private void tickMobility(Player target) {
        if (this.mobilityCooldown > 0) {
            return;
        }

        double dy = target.getY() - this.getY();
        double horizontal = Math.sqrt(this.distanceToSqr(target.getX(), this.getY(), target.getZ()));

        if (!this.getNavigation().isDone()) {
            this.pathStuckTicks = 0;
            return;
        }
        if (horizontal <= 1.8D && dy < 2.0D) {
            this.pathStuckTicks = 0;
            return;
        }

        this.pathStuckTicks++;
        if (this.pathStuckTicks < 25) {
            return;
        }

        // If the player is just above him and the path is blocked, knock the
        // block out from under the player instead of building all the way up.
        if (dy >= 2.0D && horizontal <= 3.5D && this.breakCooldown <= 0
                && this.breaksLeft > 0 && this.tryBreakUnderTarget(target)) {
            this.breakCooldown = 10 * 20;
            this.mobilityCooldown = 20;
            this.breaksLeft--;
            return;
        }

        // Build a temporary gold column when the player is above. Building
        // several blocks at once and holding position is important: with one
        // block per attempt normal pathfinding immediately walked him off the
        // fresh column.
        if (dy >= 1.0D && horizontal <= 5.0D && this.buildUpToward(target, 6)) {
            this.mobilityCooldown = 10;
            return;
        }

        // Bridge a gap when no walkable path exists.
        if (this.bridgeToward(target)) {
            this.mobilityCooldown = 10;
            this.getNavigation().moveTo(target, 1.1D);
            return;
        }

        // Do not spam attempts every tick when none of them can help.
        this.mobilityCooldown = 20;
    }

    private boolean tryBreakUnderTarget(Player target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        BlockPos under = target.blockPosition().below();
        if (under.getY() <= level.getMinBuildHeight()) {
            return false;
        }

        BlockState state = level.getBlockState(under);
        if (state.isAir() || state.getDestroySpeed(level, under) < 0.0F) {
            return false;
        }
        if (TemporaryGoldBlocks.isProtected(level, under)) {
            return false;
        }

        level.destroyBlock(under, false, this);
        return true;
    }

    private boolean buildUpToward(Player target, int maxBlocks) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        boolean built = false;
        for (int i = 0; i < maxBlocks; i++) {
            // Stop when we are level with the player's feet. A one-block step
            // is left to normal navigation/jumping.
            if (target.getY() - this.getY() < 1.0D) {
                break;
            }

            BlockPos feet = this.blockPosition();
            if (feet.getY() >= level.getMaxBuildHeight() - 2) {
                break;
            }

            BlockState atFeet = level.getBlockState(feet);
            if (!atFeet.isAir() && !atFeet.canBeReplaced()) {
                break;
            }

            BlockPos above = feet.above();
            if (level.getBlockState(above).isSolidRender(level, above)) {
                break;
            }

            if (!this.placeGold(feet)) {
                break;
            }

            // Step onto the freshly placed block. His old feet position is now
            // the support block, so the next iteration can place the next one.
            this.setPos(this.getX(), this.getY() + 1.0D, this.getZ());
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            built = true;
        }

        if (built) {
            // Keep the column stable for a moment. Normal pathfinding must not
            // move him off the edge of a one-block-wide pillar.
            this.climbTicks = Math.max(this.climbTicks, 20);
            this.stopForClimb();
        }
        return built;
    }

    private void tickClimb(Player target) {
        this.climbTicks--;
        this.stopForClimb();

        if (!isValidTarget(target)) {
            this.climbTicks = 0;
            return;
        }

        double remaining = target.getY() - this.getY();
        if (remaining < 1.0D) {
            this.climbTicks = 0;

            // At the top of the pillar he often needs to cross one or two
            // blocks of air to reach the player. Lay a small temporary
            // walkway first, otherwise he steps off the pillar.
            this.bridgeToward(target);
            this.getNavigation().moveTo(target, 1.0D);
            return;
        }

        if (!this.buildUpToward(target, 6)) {
            // A ceiling, a protected block or the budget stopped the climb.
            this.climbTicks = 0;
            this.getNavigation().moveTo(target, 1.0D);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  All-out rush                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * True while he ignores paths and simply demolishes everything between him
     * and the player: the house rush, or a chase that has already lasted a
     * whole minute.
     */
    private boolean isRushing() {
        // In the Nether he always moves like a cheater: the local terrain is
        // lava, cliffs and fortress walls, and normal pathfinding cannot
        // follow a player there.
        return this.houseRushTicks > 0 || this.desperate
                || this.level().dimension() == Level.NETHER;
    }

    /**
     * Counts how long the current chase has been going. If a whole minute
     * passes without reaching the player he switches to the all-out rush, so
     * no wall, mountain, ravine or tower can keep him away forever.
     */
    private void tickChaseTimer(Player target) {
        if (this.chaseTargetId == null || !this.chaseTargetId.equals(target.getUUID())) {
            this.chaseTargetId = target.getUUID();
            this.chaseTicks = 0;
            this.desperate = false;
            this.seksRushPending = false;
        }

        if (this.distanceToSqr(target) <= 4.0D && this.getSensing().hasLineOfSight(target)) {
            // Close enough to hit and in plain sight: the chase is over.
            this.chaseTicks = 0;
            this.seksRushPending = false;
            return;
        }

        this.chaseTicks++;

        // The warning sound comes first; the all-out rush starts only after
        // the sound has finished.
        if (this.seksRushPending) {
            if (this.tickCount >= this.seksRushAtTick) {
                this.desperate = true;
                this.seksRushPending = false;
            }
            return;
        }

        if (this.chaseTicks >= DESPERATE_TICKS && !this.desperate) {
            // A full minute without catching the player: play the special
            // sound first. The rush itself is armed for when it ends.
            this.seksRushPending = true;
            this.seksRushAtTick = this.tickCount + SEKS_RUSH_DELAY_TICKS;
            if (target instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendPlayerSound(serverPlayer, ModSounds.SEKS.get(), 1.0F, 1.0F);
            }
        }
    }

    /**
     * Covers up to twenty blocks per second straight towards the player.
     * Everything in the way is destroyed without a pause, gaps are bridged
     * with gold, and when the player is above him he pillars up at a real
     * player's pace (one block every four ticks).
     */
    private void rushTowards(Player target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        double dx = target.getX() - this.getX();
        double dy = target.getY() - this.getY();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(horizontal * horizontal + dy * dy);

        if (distance <= 1.5D && this.getSensing().hasLineOfSight(target)) {
            // Within reach and in plain sight: the melee goal finishes the job.
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            return;
        }

        // Player above / below: build up or build a staircase down. When the
        // player is below, the staircase starts immediately even from far
        // away - he must never fly horizontally above the player and only
        // then drop down. If the step is blocked (bedrock, a barrier,
        // somebody else's protected gold) the horizontal move below is used.
        boolean vertical = false;
        if (dy > 0.9D && horizontal <= 4.0D) {
            vertical = this.rushPillarUp(level);
        } else if (dy < -1.0D) {
            vertical = this.rushStairDown(level, target);
        } else if (horizontal < 0.05D) {
            vertical = dy > 0.0D ? this.rushPillarUp(level) : this.rushStairDown(level, target);
        }
        if (vertical) {
            return;
        }

        // Directly above/below him and the staircase/pillar could not be built
        // (world floor, bedrock, protected block). Dividing by a horizontal
        // distance of zero used to produce NaN coordinates, which froze the
        // whole server. Never move in that case.
        if (horizontal < 0.05D) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            return;
        }

        double step = Math.min(RUSH_STEP, horizontal);
        double nextX = this.getX() + dx / horizontal * step;
        double nextZ = this.getZ() + dz / horizontal * step;

        BlockPos nextFeet = BlockPos.containing(nextX, this.getY() + 0.05D, nextZ);
        // Clear only the one-by-two space he will actually occupy. A whole
        // 3x3x3 cube per tick (and the fluid updates it triggered next to
        // Nether lava) was enough to stall the server.
        this.carveRushBody(level, nextFeet);
        if (!this.canOccupy(level, nextFeet)) {
            // Bedrock or a barrier: do not put him inside it.
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            return;
        }

        // Blocks appear only when they are actually needed: one gold block
        // per tick over a gap, lava or water.
        BlockPos support = nextFeet.below();
        if (!level.getBlockState(support).isSolidRender(level, support)) {
            this.placeGoldRaw(level, support);
        }

        this.setPos(nextX, this.getY(), nextZ);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.setOnGround(true);
    }

    /**
     * Pillars up towards a player above him. He places exactly one gold block
     * every {@link #PILLAR_TICKS} ticks - the same rate as a real player - so
     * the climb looks like building instead of teleporting. Returns false when
     * the column cannot be cleared, so the caller can go around instead.
     */
    private boolean rushPillarUp(ServerLevel level) {
        // +0.1 avoids picking the floor block when he stands exactly on a
        // block boundary; otherwise the gold placement used to fail and he
        // simply killed the player from below.
        BlockPos feet = BlockPos.containing(this.getX(), this.getY() + 0.1D, this.getZ());
        if (feet.getY() >= level.getMaxBuildHeight() - 3) {
            return false;
        }

        // Clear the whole column he is about to stand in (one wide, three
        // high) so an overhang cannot stop the climb. Only on the tick a
        // block is actually placed, not during the three waiting ticks.
        if (this.pillarCooldown > 0) {
            this.pillarCooldown--;
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            return true;
        }

        this.carveRushArea(level, feet, 0, 2);

        if (!this.placeGoldRaw(level, feet)) {
            return false;
        }

        this.setPos(this.getX(), feet.getY() + 1.0D, this.getZ());
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.setOnGround(true);
        this.pillarCooldown = PILLAR_TICKS - 1;
        return true;
    }

    /**
     * Builds a cheater staircase down towards a player below him: one block
     * down and one block towards the target every {@link #STAIR_TICKS} ticks,
     * with a gold step placed only when there is nothing to stand on.
     */
    private boolean rushStairDown(ServerLevel level, Player target) {
        BlockPos feet = BlockPos.containing(this.getX(), this.getY() + 0.1D, this.getZ());
        if (feet.getY() <= level.getMinBuildHeight() + 1) {
            return false;
        }

        // The step is not taken every tick: a short pause makes the descent
        // readable and stops him from flying into the player.
        if (this.stairCooldown > 0) {
            this.stairCooldown--;
            this.setDeltaMovement(Vec3.ZERO);
            this.setOnGround(true);
            return true;
        }

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();

        int stepX = 0;
        int stepZ = 0;
        if (Math.abs(dx) >= Math.abs(dz) && Math.abs(dx) >= 0.3D) {
            stepX = dx > 0.0D ? 1 : -1;
        } else if (Math.abs(dz) >= 0.3D) {
            stepZ = dz > 0.0D ? 1 : -1;
        } else {
            // The player is (almost) straight below: build a tight zigzag
            // staircase instead of dropping down a one-block shaft.
            if (((this.tickCount / 2) & 1) == 0) {
                stepX = 1;
            } else {
                stepZ = 1;
            }
        }

        BlockPos next = feet.offset(stepX, -1, stepZ);
        if (next.getY() <= level.getMinBuildHeight()) {
            return false;
        }

        // Only the one-by-one space of the next step is cleared. The ground
        // below survives, so the descent looks like a real staircase; gold is
        // added only where the step would otherwise float over air or lava.
        this.carveRushArea(level, next, 0, 1);
        if (!level.getBlockState(next).getCollisionShape(level, next).isEmpty()
                || !level.getBlockState(next.above()).getCollisionShape(level, next.above()).isEmpty()) {
            // Bedrock or a barrier: do not bury him in it.
            return false;
        }

        BlockPos support = next.below();
        if (!level.getBlockState(support).isSolidRender(level, support)) {
            this.placeGoldRaw(level, support);
        }

        this.setPos(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.setOnGround(true);
        this.stairCooldown = STAIR_TICKS - 1;
        return true;
    }

    /** Destroys everything in a small box without drops. */
    private void carveRushArea(ServerLevel level, BlockPos center, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    this.mineRushBlock(level, center.offset(dx, dy, dz));
                }
            }
        }
    }

    /** Clears exactly the one-by-two space a standing mob occupies. */
    private void carveRushBody(ServerLevel level, BlockPos feet) {
        this.mineRushBlock(level, feet);
        this.mineRushBlock(level, feet.above());
    }

    private boolean canOccupy(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private void mineRushBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F
                || TemporaryGoldBlocks.isProtected(level, pos)) {
            return;
        }
        // Quiet removal: no neighbour updates, so lava next to a carved hole
        // does not start a chain reaction of fluid ticks.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    /** Gold placed by the rush is not limited by the casual gold budget. */
    private boolean placeGoldRaw(ServerLevel level, BlockPos pos) {
        // Replaces air, water and lava. Quietly (no neighbour updates): the
        // bridge must not make a whole Nether lava sea start flowing.
        return TemporaryGoldBlocks.placeForced(level, pos, this.getUUID());
    }

    /**
     * When he cannot see any player he minds his own business: he strolls
     * around the area instead of staring at the player through walls.
     */
    private void tickIdle() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.idleStrollCooldown > 0) {
            this.idleStrollCooldown--;
            return;
        }
        this.idleStrollCooldown = 140 + this.random.nextInt(140);
        if (!this.getNavigation().isDone()) {
            return;
        }

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 8.0D + this.random.nextDouble() * 16.0D;
            int x = (int) Math.floor(this.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(this.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, this.getBlockY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            this.getNavigation().moveTo(x + 0.5D, y, z + 0.5D, 0.7D);
            return;
        }
    }

    private void stopForClimb() {
        this.getNavigation().stop();
        this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setDeltaMovement(Vec3.ZERO);
        this.setOnGround(true);
    }

    private boolean bridgeToward(Player target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }

        Vec3 direction = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (direction.lengthSqr() < 0.01D) {
            return false;
        }
        direction = direction.normalize();

        BlockPos origin = this.blockPosition();
        boolean placed = false;
        for (int step = 1; step <= 3; step++) {
            int x = origin.getX() + (int) Math.round(direction.x * step);
            int z = origin.getZ() + (int) Math.round(direction.z * step);
            BlockPos support = new BlockPos(x, origin.getY() - 1, z);

            if (level.getBlockState(support).isSolidRender(level, support)) {
                continue;
            }
            if (level.getFluidState(support).is(FluidTags.WATER)) {
                continue;
            }
            if (this.placeGold(support)) {
                placed = true;
            }
        }
        return placed;
    }

    private boolean placeGold(BlockPos pos) {
        if (this.goldBudget <= 0 || !(this.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!TemporaryGoldBlocks.place(level, pos, this.getUUID())) {
            return false;
        }
        this.goldBudget--;
        return true;
    }

    private boolean canAcquire(Player player) {
        if (this.hideCooldown > 0) {
            // A successful hide really lasts thirty seconds: he searches the
            // last known position instead of magically spotting you again.
            // Standing up in his field of view still gives you away. This is
            // checked before the house-rush shortcut, otherwise the rush
            // target would be re-acquired one tick after hiding.
            return !player.isShiftKeyDown()
                    && this.distanceToSqr(player) < 36.0D
                    && this.getSensing().hasLineOfSight(player);
        }
        if (this.rushTargetId != null && this.rushTargetId.equals(player.getUUID())) {
            // The house rush target is always valid, even in creative.
            return true;
        }
        // He only starts a hunt when he can actually see you (or you are close
        // enough to hear). Otherwise he keeps living his own life.
        if (this.level().dimension() == Level.NETHER) {
            // The Nether is a maze of walls and lava: there he simply senses
            // the player and cheats his way towards them.
            return true;
        }
        return this.distanceToSqr(player) < 64.0D || this.getSensing().hasLineOfSight(player);
    }

    private boolean isHidden(Player player) {
        // Sneak, break line of sight and stop moving. Walking behind a tree
        // while still moving does not count as hiding.
        if (!player.isShiftKeyDown() || this.getSensing().hasLineOfSight(player)) {
            this.hideAnchor = null;
            return false;
        }
        if (this.hideAnchor == null) {
            this.hideAnchor = player.position();
            return true;
        }
        double movedX = player.getX() - this.hideAnchor.x;
        double movedZ = player.getZ() - this.hideAnchor.z;
        if (movedX * movedX + movedZ * movedZ > 0.25D) {
            // More than half a block during the hide: start over.
            this.hideAnchor = player.position();
            return false;
        }
        return true;
    }

    private void loseTarget(Player player) {
        this.setTarget(null);
        this.lostTicks = 0;
        this.hideVisibleTicks = 0;
        this.escapeTicks = 0;
        this.hideAnchor = null;
        this.climbTicks = 0;
        this.pillarCooldown = 0;
        this.stairCooldown = 0;
        this.hideCooldown = HIDE_COOLDOWN_TICKS;
        this.searchTicks = SEARCH_TICKS;
        // lastKnownPos is deliberately NOT updated here: it must remain the
        // place where he actually saw the player, not the hiding spot.
        this.lostSoundTick = this.tickCount + LOST_SOUND_DELAY;
        this.desperate = false;
        this.seksRushPending = false;
        this.chaseTicks = 0;
        this.chaseTargetId = null;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.mellstroy.hidden")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    true);
        }
    }

    private void onSpotted(Player player) {
        this.lostTicks = 0;
        this.hideVisibleTicks = 0;
        this.escapeTicks = 0;
        this.hideAnchor = null;
        this.searchTicks = 0;
        this.lostSoundTick = -1;
        // Let "I see you" finish before the first chase phrase can start.
        this.nextPhraseTick = this.tickCount
                + (int) Math.ceil(ModSounds.UVIDEL_SECONDS * 20.0F) + 40;
        this.playTrackedSound(ModSounds.UVIDEL.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.mellstroy.spotted")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    true);
        }
    }

    private void applyFear(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        double distSqr = player.distanceToSqr(this);
        if (distSqr < 24.0D * 24.0D) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false, false));
        }
        if (distSqr < 12.0D * 12.0D) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
        }
    }

    /**
     * After standing still for three seconds he stops trying to path around the
     * obstacle and simply tunnels through it: a three-wide, three-high corridor
     * straight towards the player. The block under his own feet is never
     * touched, and he is moved into the freshly opened tunnel so the dig always
     * results in actual progress.
     */
    private void tickBreakThroughObstacles() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(this.getTarget() instanceof Player target) || !isValidTarget(target)
                || this.distanceToSqr(target) < 3.5D * 3.5D) {
            this.stuckTicks = 0;
            this.previousStuckX = this.getX();
            this.previousStuckZ = this.getZ();
            return;
        }
        if (this.climbTicks > 0) {
            // Building a column up to the player does not move him sideways;
            // that must not be mistaken for being stuck.
            this.stuckTicks = 0;
            this.previousStuckX = this.getX();
            this.previousStuckZ = this.getZ();
            return;
        }
        double movedX = Double.isNaN(this.previousStuckX) ? 1.0D : this.getX() - this.previousStuckX;
        double movedZ = Double.isNaN(this.previousStuckZ) ? 1.0D : this.getZ() - this.previousStuckZ;
        this.previousStuckX = this.getX();
        this.previousStuckZ = this.getZ();
        if (movedX * movedX + movedZ * movedZ < 0.0025D) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        if (this.stuckTicks < STUCK_BREAK_TICKS) {
            return;
        }

        BlockPos feet = this.blockPosition();
        boolean broke = false;

        // If he is actually wedged inside terrain (for example a waterfall
        // pushed him into the dirt), free his own body first. The block under
        // his feet is never touched - that one holds him up.
        for (BlockPos inside : new BlockPos[]{feet, feet.above()}) {
            if (inside.equals(feet) && this.getY() - feet.getY() >= 0.6D) {
                // Standing on top of this block (including the usual tiny
                // floating-point sink), not inside it.
                continue;
            }
            BlockState state = level.getBlockState(inside);
            if (state.isSuffocating(level, inside) && state.getDestroySpeed(level, inside) >= 0.0F
                    && !TemporaryGoldBlocks.isProtected(level, inside)) {
                level.destroyBlock(inside, true, this);
                broke = true;
            }
        }

        // Dragon mode: tunnel towards the player, not towards wherever his
        // head happens to be looking while he is jumping against a wall.
        broke |= digTunnelToward(level, target, feet, 5);

        if (broke) {
            this.stuckTicks = STUCK_BREAK_TICKS - 6;
            this.advanceThroughTunnel(level, target, feet);
            this.getNavigation().moveTo(target, 1.0D);
        } else {
            // Nothing solid in front of him: let the pathfinder retry soon and
            // give him a small hop for a one-block ledge.
            this.stuckTicks = STUCK_BREAK_TICKS - 20;
            this.getNavigation().moveTo(target, 1.0D);
            this.getJumpControl().jump();
        }
    }

    /**
     * Carves a walkable corridor towards the target: three blocks wide, three
     * blocks tall and {@code depth} blocks deep. Blocks under his feet and the
     * floor of the corridor are left alone.
     */
    private boolean digTunnelToward(ServerLevel level, Player target, BlockPos feet, int depth) {
        int dx = target.getBlockX() - feet.getX();
        int dz = target.getBlockZ() - feet.getZ();
        int dirX = Integer.compare(dx, 0);
        int dirZ = Integer.compare(dz, 0);
        // A single axis keeps the tunnel wide enough to walk through.
        if (Math.abs(dx) >= Math.abs(dz)) {
            dirZ = 0;
        } else {
            dirX = 0;
        }
        if (dirX == 0 && dirZ == 0) {
            return false;
        }

        int sideX = dirZ != 0 ? 1 : 0;
        int sideZ = dirX != 0 ? 1 : 0;
        boolean broke = false;

        for (int step = 1; step <= depth; step++) {
            BlockPos column = feet.offset(dirX * step, 0, dirZ * step);
            for (int side = -1; side <= 1; side++) {
                BlockPos base = column.offset(sideX * side, 0, sideZ * side);
                for (int up = 0; up <= 2; up++) {
                    broke |= mineForTunnel(level, base.above(up));
                }
            }
        }
        return broke;
    }

    private boolean mineForTunnel(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F
                || TemporaryGoldBlocks.isProtected(level, pos)) {
            return false;
        }
        // No drops: a dragon does not leave a loot trail, and hundreds of item
        // entities would lag the server.
        level.destroyBlock(pos, false, this);
        return true;
    }

    /** Steps into the tunnel he just carved so he actually makes progress. */
    private void advanceThroughTunnel(ServerLevel level, Player target, BlockPos feet) {
        int dx = target.getBlockX() - feet.getX();
        int dz = target.getBlockZ() - feet.getZ();
        int dirX = Integer.compare(dx, 0);
        int dirZ = Integer.compare(dz, 0);
        if (Math.abs(dx) >= Math.abs(dz)) {
            dirZ = 0;
        } else {
            dirX = 0;
        }
        if (dirX == 0 && dirZ == 0) {
            return;
        }

        BlockPos ahead = feet.offset(dirX, 0, dirZ);
        for (int up = 0; up <= 1; up++) {
            BlockPos candidate = ahead.above(up);
            BlockPos below = candidate.below();
            if (!level.getBlockState(below).isSolidRender(level, below)) {
                continue;
            }
            if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    || !level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
                continue;
            }
            this.setPos(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0F;
            this.setOnGround(true);
            return;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Water escape                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * True when he is submerged, standing in a waterfall, or has been pushed
     * inside blocks under flowing water. Normal navigation cannot get him out
     * of those, so he is teleported to dry land.
     */
    private boolean needsWaterEscape() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos feet = this.blockPosition();
        if (this.isUnderWater()
                || isFallingWater(level.getFluidState(feet))
                || isFallingWater(level.getFluidState(feet.above()))) {
            return true;
        }
        boolean buried = level.getBlockState(feet).isSuffocating(level, feet)
                || level.getBlockState(feet.above()).isSuffocating(level, feet.above());
        return buried && hasWaterNearby(level, feet, 2);
    }

    private static boolean hasWaterNearby(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Teleports him out of the water onto dry land. The landing spot is always
     * at least {@link #WATER_ESCAPE_CLEARANCE} blocks above the water he was
     * in and roughly at the player's height, but never right next to them.
     */
    private void escapeWaterToPlayerHeight() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Player nearest = findNearestPlayer();
        if (nearest == null) {
            return;
        }

        BlockPos origin = this.blockPosition();
        int clearanceY = waterSurfaceY(level, origin) + WATER_ESCAPE_CLEARANCE;
        int desiredY = Math.max(nearest.getBlockY(), clearanceY);

        BlockPos destination = findDryLanding(level, origin, nearest, desiredY, clearanceY, 72,
                WATER_ESCAPE_PLAYER_GAP);
        if (destination == null) {
            // Last resort: a wider search that only keeps a small gap.
            destination = findDryLanding(level, origin, nearest, desiredY, clearanceY, 96, 16.0D);
        }
        if (destination == null) {
            return;
        }

        this.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.getNavigation().stop();
    }

    @Nullable
    private Player findNearestPlayer() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : this.level().players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            double distance = this.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /** Topmost water block in his column, including falling water above him. */
    private static int waterSurfaceY(ServerLevel level, BlockPos origin) {
        int top = origin.getY() - 1;
        for (int y = origin.getY() - 2; y <= origin.getY() + 8; y++) {
            if (level.getFluidState(new BlockPos(origin.getX(), y, origin.getZ())).is(FluidTags.WATER)) {
                top = y;
            }
        }
        return Math.max(top, origin.getY() - 1);
    }

    @Nullable
    private static BlockPos findDryLanding(ServerLevel level, BlockPos origin, Player player,
                                           int desiredY, int minimumY, int maxRadius, double playerGap) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int radius = 8; radius <= maxRadius; radius += 4) {
            for (int attempt = 0; attempt < 64; attempt++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
                if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < minimumY) {
                    continue;
                }
                BlockPos feet = new BlockPos(x, y, z);
                BlockPos ground = feet.below();
                if (!level.getBlockState(ground).isSolidRender(level, ground)
                        || !level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()
                        || !level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(feet.above()).isEmpty()) {
                    continue;
                }
                double playerDistance = player.distanceToSqr(x + 0.5D, y, z + 0.5D);
                if (playerDistance < playerGap * playerGap) {
                    continue;
                }
                double score = Math.abs(y - desiredY) * 4.0D
                        + Math.abs(y - origin.getY()) + radius * 0.02D;
                if (score < bestScore) {
                    best = feet;
                    bestScore = score;
                }
            }
            if (best != null && Math.abs(best.getY() - desiredY) <= 4) {
                break;
            }
        }
        return best;
    }

    public void summonForHouse(Player target) {
        this.rushTargetId = target.getUUID();
        this.houseRushTicks = 20 * 120;
        this.lostTicks = 0;
        this.hideVisibleTicks = 0;
        this.escapeTicks = 0;
        this.pillarCooldown = 0;
        this.stairCooldown = 0;
        this.hideCooldown = 0;
        this.searchTicks = 0;
        this.lostSoundTick = -1;
        this.climbTicks = 0;
        this.chaseTargetId = target.getUUID();
        this.chaseTicks = 0;
        // isRushing() is already true because of houseRushTicks. Keep the
        // desperate flag free so the one-minute all-out sound can still play
        // if this rush lasts that long.
        this.desperate = false;
        this.seksRushPending = false;
        this.setTarget(target);
        this.setMovementSpeed(0.8D);
    }

    /**
     * Keeps an active house rush locked on the player who broke the block.
     * Called by the alarm every time it ticks, so a mob that somehow lost the
     * target re-acquires it instead of wandering off.
     */
    public void keepRushing(Player target) {
        if (this.hideCooldown > 0 && this.getTarget() == null) {
            // The player hid successfully: let him search for the promised
            // thirty seconds instead of snapping straight back onto them.
            return;
        }
        if (this.houseRushTicks <= 0 || this.rushTargetId == null
                || !this.rushTargetId.equals(target.getUUID())) {
            this.summonForHouse(target);
        } else if (!(this.getTarget() instanceof Player current) || current != target) {
            this.setTarget(target);
        }
    }

    private void setMovementSpeed(double speed) {
        var attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(speed);
        }
    }

    @Nullable
    private Player findNearestTarget() {
        double range = MellstroyConfig.huntRange();
        if (this.level().dimension() == Level.NETHER) {
            // He can be spawned further away than the normal hunt range in
            // the Nether (the terrain is too broken for short searches).
            range = Math.max(range, 128.0D);
        }
        Player best = null;
        double bestDistance = range * range;

        for (Player player : this.level().players()) {
            if (!isValidTarget(player)) {
                continue;
            }
            double distance = this.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private boolean isValidTarget(Player player) {
        if (!player.isAlive()
                || player.isDeadOrDying()
                || player.isSpectator()
                || player.level() != this.level()) {
            return false;
        }
        // During a house rush he keeps chasing the player who broke the block,
        // even if that player is in creative mode.
        if (this.rushTargetId != null && this.rushTargetId.equals(player.getUUID())) {
            return true;
        }
        return !player.isCreative();
    }

    public boolean isHunting() {
        return this.entityData.get(DATA_HUNTING);
    }

    /**
     * True while he is already busy with a player: actively hunting one, in a
     * house rush, or searching for someone who just hid. There is only one
     * Mellstroy in the world, so a second event (for example another player
     * breaking a block in a house) must not summon him again while this is
     * true: the fresh copy would delete him mid-chase and look like he
     * instantly switched targets.
     */
    public boolean isOccupied() {
        if (this.getTarget() != null || this.isHunting() || this.searchTicks > 0) {
            return true;
        }
        if (this.houseRushTicks > 0 && this.rushTargetId != null) {
            // A rush only keeps him busy while the player it was called for is
            // still here. Once that player is dead or logged out he is free
            // again, so somebody else's house break can summon him.
            MinecraftServer server = this.level().getServer();
            ServerPlayer rushTarget = server == null ? null
                    : server.getPlayerList().getPlayer(this.rushTargetId);
            return rushTarget != null && rushTarget.isAlive()
                    && rushTarget.serverLevel() == this.level();
        }
        return false;
    }

    /** True when any loaded Mellstroy in any dimension is busy with someone. */
    public static boolean isAnyOccupied(@Nullable MinecraftServer server) {
        if (server == null) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (MellstroyEntity mob : level.getEntitiesOfClass(MellstroyEntity.class, EVERYWHERE)) {
                if (mob.isOccupied()) {
                    return true;
                }
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /*  Casino helpers                                                    */
    /* ------------------------------------------------------------------ */

    /** Stops him completely (used while the casino screen is open). */
    public void freezeInPlace() {
        this.setTarget(null);
        this.setNoAi(true);
        this.climbTicks = 0;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    public void unfreeze() {
        this.setNoAi(false);
    }

    /** Makes him ignore every player for a while. */
    public void giveGrace(int ticks) {
        this.graceTicks = ticks;
        this.hideCooldown = ticks;
        this.lostSoundTick = -1;
        this.climbTicks = 0;
        this.setTarget(null);
        this.setLastHurtByMob(null);
    }

    /* ------------------------------------------------------------------ */
    /*  Water walking                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.WATER) || super.canStandOnFluid(fluid);
    }

    @Override
    public void updateSwimming() {
        // He runs over the surface instead of entering the swimming pose.
        this.setSwimming(false);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && !this.isNoAi() && this.isInWater()) {
            this.walkOnWater();
        }
    }

    private void walkOnWater() {
        BlockPos feet = this.blockPosition();
        FluidState feetFluid = this.level().getFluidState(feet);

        // Only search upward when the block at his feet is still water. A
        // waterfall pouring onto him must NOT make him climb the whole falling
        // column: falling water has no stable surface and he would be pushed
        // up into ceilings/blocks. In that case use the water level at his feet.
        int topY = feet.getY();
        if (feetFluid.is(FluidTags.WATER) && !isFallingWater(feetFluid)) {
            int limit = Math.min(this.level().getMaxBuildHeight() - 1, feet.getY() + 64);

            for (int y = feet.getY() + 1; y <= limit; y++) {
                BlockPos check = new BlockPos(feet.getX(), y, feet.getZ());
                FluidState checkFluid = this.level().getFluidState(check);
                if (!checkFluid.is(FluidTags.WATER) || isFallingWater(checkFluid)) {
                    break;
                }
                topY = y;
            }
        }

        BlockPos topPos = new BlockPos(feet.getX(), topY, feet.getZ());
        FluidState fluid = this.level().getFluidState(topPos);
        double surface = topY + fluid.getHeight(this.level(), topPos);
        double targetY = surface - 0.05D;

        if (this.getY() < targetY) {
            this.setPos(this.getX(), Math.min(targetY, this.getY() + 0.6D), this.getZ());
        } else if (this.getY() > targetY + 0.2D) {
            this.setPos(this.getX(), targetY, this.getZ());
        }

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, 0.0D, motion.z);
        this.fallDistance = 0.0F;
        // Grounded on the water surface: navigation can update and he can jump
        // over small obstacles without switching to swimming.
        this.setOnGround(true);
    }

    private static boolean isFallingWater(FluidState fluid) {
        return fluid.is(FluidTags.WATER)
                && fluid.hasProperty(FlowingFluid.FALLING)
                && fluid.getValue(FlowingFluid.FALLING);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level) {
            TemporaryGoldBlocks.clearMob(level, this.getUUID());
        }
        if (activeId != null && activeId.equals(this.getUUID())) {
            activeId = null;
        }
        super.remove(reason);
    }

    /* ------------------------------------------------------------------ */
    /*  Combat / immortality                                              */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }

        float damage = MellstroyConfig.oneHitKill()
                ? 4096.0F
                : (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

        boolean hurt = living.hurt(this.damageSources().mobAttack(this), damage);
        if (hurt) {
            living.setDeltaMovement(living.getDeltaMovement().add(0.0D, 0.35D, 0.0D));
            if (living instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.mellstroy.caught")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        true);
            }
        }
        return hurt;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Only things that bypass invulnerability (e.g. /kill) can hurt him.
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    /* ------------------------------------------------------------------ */
    /*  Spawning                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Enforces "exactly one Mellstroy in the world". Called for every join.
     * A fresh spawn always replaces the old one; an old copy that is only
     * being loaded from disk is removed because a newer one is active.
     *
     * @return true when this join must be cancelled because the entity is an
     *         outdated copy.
     */
    public static boolean handleJoin(ServerLevel level, MellstroyEntity mob, boolean loadedFromDisk) {
        if (loadedFromDisk) {
            if (activeId == null || activeId.equals(mob.getUUID())) {
                activeId = mob.getUUID();
                return false;
            }
            // An old copy is loading after the current one: drop it.
            mob.discard();
            return true;
        }

        activeId = mob.getUUID();
        purgeOthers(level, mob.getUUID());
        return false;
    }

    private static void purgeOthers(ServerLevel level, UUID keep) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel other : server.getAllLevels()) {
            for (MellstroyEntity mob : other.getEntitiesOfClass(MellstroyEntity.class, EVERYWHERE)) {
                if (!mob.getUUID().equals(keep)) {
                    mob.discard();
                }
            }
        }
    }

    /** Called when the server stops so a new world starts from a clean slate. */
    public static void resetActive() {
        activeId = null;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        this.setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // He walks in complete silence.
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }
}
