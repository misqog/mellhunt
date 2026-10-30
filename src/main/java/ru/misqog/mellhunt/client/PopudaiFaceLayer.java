package ru.misqog.mellhunt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.PopudaiEntity;

/**
 * Draws the full-resolution 64x64 photo on the front of the 12x12 head.
 *
 * <p>The cube's own front face is only 12x12 pixels; this layer puts a flat
 * quad with the complete photo texture just in front of it, so the face keeps
 * far more detail than a cube UV could.</p>
 */
public class PopudaiFaceLayer extends RenderLayer<PopudaiEntity, PopudaiModel<PopudaiEntity>> {

    public static final ResourceLocation FACE =
            new ResourceLocation(MellstroyMod.MOD_ID, "textures/entity/popudai_face.png");

    public PopudaiFaceLayer(RenderLayerParent<PopudaiEntity, PopudaiModel<PopudaiEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       PopudaiEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ModelPart head = this.getParentModel().headPart();

        poseStack.pushPose();
        head.translateAndRotate(poseStack);

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(FACE));

        float x0 = -6.0F / 16.0F;
        float x1 = 6.0F / 16.0F;
        float y0 = -6.0F / 16.0F;
        float y1 = 6.0F / 16.0F;
        float z = -6.05F / 16.0F;

        vertices.vertex(matrix, x0, y0, z)
                .color(255, 255, 255, 255)
                .uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, -1.0F)
                .endVertex();
        vertices.vertex(matrix, x1, y0, z)
                .color(255, 255, 255, 255)
                .uv(1.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, -1.0F)
                .endVertex();
        vertices.vertex(matrix, x1, y1, z)
                .color(255, 255, 255, 255)
                .uv(1.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, -1.0F)
                .endVertex();
        vertices.vertex(matrix, x0, y1, z)
                .color(255, 255, 255, 255)
                .uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 0.0F, -1.0F)
                .endVertex();

        poseStack.popPose();
    }
}
