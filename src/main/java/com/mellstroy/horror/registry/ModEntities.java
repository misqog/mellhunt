package com.mellstroy.horror.registry;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.entity.FatCatEntity;
import com.mellstroy.horror.entity.MellstroyEntity;
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

    private ModEntities() {
    }
}
