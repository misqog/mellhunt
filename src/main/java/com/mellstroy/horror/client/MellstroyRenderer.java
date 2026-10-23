package com.mellstroy.horror.client;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.entity.MellstroyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Mellstroy uses the vanilla player model, so he moves like a person.
 * The texture is a generated 64x64-style skin based on his photo.
 */
public class MellstroyRenderer extends MobRenderer<MellstroyEntity, HumanoidModel<MellstroyEntity>> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MellstroyMod.MOD_ID, "textures/entity/mellstroy.png");

    public MellstroyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(MellstroyEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MellstroyEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(1.05F, 1.05F, 1.05F);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
