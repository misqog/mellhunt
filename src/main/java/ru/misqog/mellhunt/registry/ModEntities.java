package ru.misqog.mellhunt.registry;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.ChairSeatEntity;
import ru.misqog.mellhunt.entity.FatCatEntity;
import ru.misqog.mellhunt.entity.MellstroyEntity;
import ru.misqog.mellhunt.entity.PopudaiEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MellstroyMod.MOD_ID);

    public static final RegistryObject<EntityType<MellstroyEntity>> MELLSTROY = ENTITY_TYPES.register("mellstroy",
            () -> EntityType.Builder.<MellstroyEntity>of(MellstroyEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(160)
                    .updateInterval(2)
                    .fireImmune()
                    .build(new ResourceLocation(MellstroyMod.MOD_ID, "mellstroy").toString()));

    public static final RegistryObject<EntityType<FatCatEntity>> FAT_CAT = ENTITY_TYPES.register("fat_cat",
            () -> EntityType.Builder.<FatCatEntity>of(FatCatEntity::new, MobCategory.CREATURE)
                    .sized(1.0F, 1.05F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(MellstroyMod.MOD_ID, "fat_cat").toString()));

    /** Meme parrot with a human face; a tameable pet. */
    public static final RegistryObject<EntityType<PopudaiEntity>> POPUDAI = ENTITY_TYPES.register("popudai",
            () -> EntityType.Builder.<PopudaiEntity>of(PopudaiEntity::new, MobCategory.CREATURE)
                    .sized(0.55F, 0.7F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(MellstroyMod.MOD_ID, "popudai").toString()));

    /** The invisible seat used by casino chairs. Never spawned by the player. */
    public static final RegistryObject<EntityType<ChairSeatEntity>> CHAIR_SEAT = ENTITY_TYPES.register("chair_seat",
            () -> EntityType.Builder.<ChairSeatEntity>of(ChairSeatEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(new ResourceLocation(MellstroyMod.MOD_ID, "chair_seat").toString()));

    private ModEntities() {
    }
}
