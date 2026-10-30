package ru.misqog.mellhunt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.PopudaiEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PopudaiRenderer extends MobRenderer<PopudaiEntity, PopudaiModel<PopudaiEntity>> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(MellstroyMod.MOD_ID, "textures/entity/popudai.png");

    public PopudaiRenderer(EntityRendererProvider.Context context) {
        super(context, new PopudaiModel<>(context.bakeLayer(PopudaiModel.LAYER_LOCATION)), 0.2F);
        this.addLayer(new PopudaiFaceLayer(this));
    }

    @Override
    protected void scale(PopudaiEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.42F, 0.42F, 0.42F);
    }

    @Override
    public ResourceLocation getTextureLocation(PopudaiEntity entity) {
        return TEXTURE;
    }
}
