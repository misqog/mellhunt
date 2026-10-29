package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.entity.ChairSeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The chair seat is invisible - it only carries the sitting player. */
public class ChairSeatRenderer extends EntityRenderer<ChairSeatEntity> {

    private static final ResourceLocation EMPTY =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    public ChairSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChairSeatEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Nothing to draw.
    }

    @Override
    public ResourceLocation getTextureLocation(ChairSeatEntity entity) {
        return EMPTY;
    }
}
