package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.FatCatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FatCatRenderer extends MobRenderer<FatCatEntity, FatCatModel<FatCatEntity>> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MellstroyMod.MOD_ID, "textures/entity/fat_cat.png");

    public FatCatRenderer(EntityRendererProvider.Context context) {
        super(context, new FatCatModel<>(context.bakeLayer(FatCatModel.LAYER_LOCATION)), 0.45F);
    }

    @Override
    protected void scale(FatCatEntity entity, PoseStack poseStack, float partialTick) {
        // HierarchicalModel does not apply the vanilla "young" model swap, so
        // scale the whole kitten down explicitly. The model's feet are at the
        // origin after the renderer's -1.501 translate, so this keeps the
        // kitten standing on the ground.
        if (entity.isBaby()) {
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }
    }

    @Override
    public void render(FatCatEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        this.shadowRadius = entity.isBaby() ? 0.22F : 0.45F;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FatCatEntity entity) {
        return TEXTURE;
    }
}
