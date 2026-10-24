package com.mellstroy.horror.entity;

import com.mellstroy.horror.network.ModNetwork;
import com.mellstroy.horror.registry.ModEntities;
import com.mellstroy.horror.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

/**
 * The fat calico cat from the photo: a tameable loaf that eats any fish.
 * It is deliberately slow and has no attack goals - it is here to be petted,
 * fed and bred, not to fight.
 */
public class FatCatEntity extends TamableAnimal {

    private static final int HOUSE_SIGHT_INTERVAL = 10;
    private static final double HOUSE_SIGHT_DISTANCE_SQR = 16.0D * 16.0D;
    /** cos(60 degrees): the cat has to be inside a fairly narrow view cone. */
    private static final double HOUSE_SIGHT_MIN_DOT = 0.5D;

    /** True for the cats that spawn inside Mellstroy's house. */
    private boolean houseCat;

    public FatCatEntity(EntityType<? extends FatCatEntity> type, Level level) {
        super(type, level);
    }

    public boolean isHouseCat() {
        return this.houseCat;
    }

    public void setHouseCat(boolean houseCat) {
        this.houseCat = houseCat;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.1D));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new TemptGoal(this, 0.9D, Ingredient.of(ItemTags.FISHES), false));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0D, 10.0F, 3.0F, false));
        this.goalSelector.addGoal(5, new BreedGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.65D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FISHES);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (this.level().isClientSide) {
            if (this.isTame() && this.isOwnedBy(player)) {
                return InteractionResult.SUCCESS;
            }
            return !this.isFood(stack) || !(this.getHealth() < this.getMaxHealth()) && this.isTame()
                    ? InteractionResult.PASS
                    : InteractionResult.SUCCESS;
        }

        if (this.isTame()) {
            if (this.isOwnedBy(player)) {
                if (this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
                    this.heal(4.0F);
                    this.usePlayerItem(player, hand, stack);
                    return InteractionResult.CONSUME;
                }

                InteractionResult result = super.mobInteract(player, hand);
                if (!result.consumesAction() && (stack.isEmpty() || !this.isFood(stack))) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    this.jumping = false;
                    this.navigation.stop();
                    this.setTarget(null);
                }
                return result;
            }
        } else if (this.isFood(stack)) {
            this.usePlayerItem(player, hand, stack);

            if (this.random.nextInt(3) == 0 && !ForgeEventFactory.onAnimalTame(this, player)) {
                this.tame(player);
                this.setOrderedToSit(true);
                this.setInSittingPose(true);
                this.navigation.stop();
                this.setTarget(null);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }

            this.setPersistenceRequired();
            return InteractionResult.CONSUME;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (this.isFood(stack)) {
            this.playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
        }
        super.usePlayerItem(player, hand, stack);
    }

    @Nullable
    @Override
    public FatCatEntity getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        FatCatEntity baby = ModEntities.FAT_CAT.get().create(level);
        if (baby != null) {
            if (this.isTame()) {
                baby.setOwnerUUID(this.getOwnerUUID());
                baby.setTame(true);
            } else if (otherParent instanceof FatCatEntity other && other.isTame()) {
                baby.setOwnerUUID(other.getOwnerUUID());
                baby.setTame(true);
            }
        }
        return baby;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.houseCat && this.isAlive()
                && this.tickCount % HOUSE_SIGHT_INTERVAL == 0) {
            this.checkHouseFirstSight();
        }
    }

    /**
     * The first time a player genuinely sees a house cat, play the custom sound
     * for that player. "Genuinely" means close, inside the view cone and with a
     * clear line of sight - a cat far away or behind a window does not count.
     */
    private void checkHouseFirstSight() {
        if (this.isInvisible()) {
            return;
        }
        for (Player player : this.level().players()) {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !serverPlayer.isAlive()
                    || serverPlayer.isSpectator()) {
                continue;
            }

            CompoundTag data = serverPlayer.getPersistentData();
            if (data.getBoolean("mellstroyHouseCatSeen")) {
                continue;
            }

            Vec3 eye = serverPlayer.getEyePosition(1.0F);
            Vec3 catEye = this.getEyePosition(1.0F);
            if (eye.distanceToSqr(catEye) > HOUSE_SIGHT_DISTANCE_SQR) {
                continue;
            }

            Vec3 toCat = catEye.subtract(eye);
            if (toCat.lengthSqr() < 1.0E-4D
                    || serverPlayer.getViewVector(1.0F).dot(toCat.normalize()) < HOUSE_SIGHT_MIN_DOT) {
                continue;
            }

            if (!serverPlayer.hasLineOfSight(this)) {
                continue;
            }

            data.putBoolean("mellstroyHouseCatSeen", true);
            ModNetwork.sendPlayerSound(serverPlayer, ModSounds.MALISHOCHEK.get(), 1.0F, 1.0F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HouseCat", this.houseCat);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.houseCat = tag.getBoolean("HouseCat");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.isTame() && !this.hasCustomName();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTame() && this.random.nextInt(4) == 0 ? SoundEvents.CAT_PURR : SoundEvents.CAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 180;
    }
}
