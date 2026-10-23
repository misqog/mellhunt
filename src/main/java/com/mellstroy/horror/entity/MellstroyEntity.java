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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

    /** Ticks the player has to stay hidden before he loses them. */
    private static final int HIDE_TICKS = 40;
    /** How long he searches after losing a player. */
    private static final int HIDE_COOLDOWN_TICKS = 30 * 20;
    /** How long he keeps walking towards the last known position. */
    private static final int SEARCH_TICKS = 10 * 20;
    /** "Ne dognal" is played this long after he loses you. */
    private static final int LOST_SOUND_DELAY = 7 * 20;
    /** After a casino win he ignores the player for a while. */
    public static final int GRACE_TICKS = 30 * 20;

    private int lostTicks;
    private int hideCooldown;
    private int searchTicks;
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
        this.setCustomNameVisible(true);
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
            this.lastKnownPos = target.position();
            if (isHidden(target)) {
                this.lostTicks++;
                if (this.lostTicks >= HIDE_TICKS) {
                    loseTarget(target);
                    target = null;
                }
            } else {
                this.lostTicks = 0;
            }
        } else {
            this.pathStuckTicks = 0;
        }

        boolean hunting = target != null && isValidTarget(target);
        if (this.entityData.get(DATA_HUNTING) != hunting) {
            this.entityData.set(DATA_HUNTING, hunting);
        }

        tickPhrases(hunting);

        if (hunting && target != null) {
            this.applyFear(target);
            if (this.climbTicks > 0) {
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
        if (!hunting || this.tickCount < this.nextPhraseTick) {
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
            // While sulking he can still hear you if you stand right next to him.
            return this.distanceToSqr(player) < 36.0D && this.getSensing().hasLineOfSight(player);
        }
        return true;
    }

    private boolean isHidden(Player player) {
        return player.isShiftKeyDown() && !this.getSensing().hasLineOfSight(player);
    }

    private void loseTarget(Player player) {
        this.setTarget(null);
        this.lostTicks = 0;
        this.climbTicks = 0;
        this.hideCooldown = HIDE_COOLDOWN_TICKS;
        this.searchTicks = SEARCH_TICKS;
        this.lastKnownPos = player.position();
        this.lostSoundTick = this.tickCount + LOST_SOUND_DELAY;

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.mellstroy.hidden")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    true);
        }
    }

    private void onSpotted(Player player) {
        this.lostTicks = 0;
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
        double distSqr = player.distanceToSqr(this);
        if (distSqr < 24.0D * 24.0D) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false, false));
        }
        if (distSqr < 12.0D * 12.0D) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
        }
    }

    @Nullable
    private Player findNearestTarget() {
        double range = MellstroyConfig.huntRange();
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
        return player.isAlive()
                && !player.isDeadOrDying()
                && !player.isCreative()
                && !player.isSpectator()
                && player.level() == this.level();
    }

    public boolean isHunting() {
        return this.entityData.get(DATA_HUNTING);
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

    /* ------------------------------------------------------------------ */
    /*  Spawning                                                          */
    /* ------------------------------------------------------------------ */

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
