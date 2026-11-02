package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import ru.misqog.mellhunt.entity.CashierEntity;

/**
 * The casino cashier in a black-and-gold uniform. She uses the vanilla player
 * model, so she stands and moves her head exactly like casino staff should.
 */
public class CashierRenderer extends MobRenderer<CashierEntity, HumanoidModel<CashierEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MellstroyMod.MOD_ID, "textures/entity/cashier.png");

    public CashierRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CashierEntity entity) {
        return TEXTURE;
    }
}
