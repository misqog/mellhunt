package com.mellstroy.horror.client;

import com.mellstroy.horror.MellstroyMod;
import com.mellstroy.horror.entity.FatCatEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A truly volumetric fat calico: pear-shaped body built from stacked boxes,
 * a huge head sunk into the shoulders, chubby cheeks, tiny folded ears and
 * short paws. Feet are at model y = 24, like every vanilla mob.
 */
public class FatCatModel<T extends FatCatEntity> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(MellstroyMod.MOD_ID, "fat_cat"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;
    private final ModelPart tail;
    private final ModelPart tailTip;

    public FatCatModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.backLeftLeg = root.getChild("back_left_leg");
        this.backRightLeg = root.getChild("back_right_leg");
        this.tail = root.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7.0F, -6.0F, -8.0F, 14.0F, 9.0F, 16.0F)
                        .texOffs(0, 32).addBox(-6.0F, -8.0F, -7.0F, 12.0F, 3.0F, 14.0F)
                        .texOffs(64, 0).addBox(-7.5F, 0.1F, -8.5F, 15.0F, 3.0F, 17.0F)
                        .texOffs(64, 32).addBox(-5.0F, -3.0F, -9.5F, 10.0F, 5.0F, 2.0F),
                PartPose.offset(0.0F, 19.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 64).addBox(-4.5F, -4.5F, -4.5F, 9.0F, 9.0F, 9.0F)
                        .texOffs(64, 64).addBox(-3.0F, 1.6F, -5.5F, 6.0F, 3.0F, 2.0F)
                        .texOffs(96, 0).addBox(4.0F, -1.5F, -3.5F, 2.0F, 5.0F, 6.0F)
                        .texOffs(96, 0).addBox(-6.0F, -1.5F, -3.5F, 2.0F, 5.0F, 6.0F),
                PartPose.offset(0.0F, 13.0F, -5.5F));

        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(96, 24).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(3.0F, -4.0F, -3.0F, -0.15F, 0.0F, -0.18F));
        head.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(96, 32).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, -4.0F, -3.0F, -0.15F, 0.0F, 0.18F));

        root.addOrReplaceChild("front_right_leg",
                CubeListBuilder.create().texOffs(96, 40).addBox(-1.5F, 0.0F, -1.4F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(4.0F, 20.0F, -8.0F));
        root.addOrReplaceChild("front_left_leg",
                CubeListBuilder.create().texOffs(96, 40).addBox(-1.5F, 0.0F, -1.4F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(-4.0F, 20.0F, -8.0F));
        root.addOrReplaceChild("back_right_leg",
                CubeListBuilder.create().texOffs(96, 40).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(4.5F, 20.0F, 7.1F));
        root.addOrReplaceChild("back_left_leg",
                CubeListBuilder.create().texOffs(96, 40).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(-4.5F, 20.0F, 7.1F));

        PartDefinition tail = root.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(128, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 5.0F),
                PartPose.offset(0.0F, 16.0F, 7.9F));
        tail.addOrReplaceChild("tail_tip",
                CubeListBuilder.create().texOffs(128, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 4.9F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.frontLeftLeg.visible = true;
        this.frontRightLeg.visible = true;
        this.backLeftLeg.visible = true;
        this.backRightLeg.visible = true;

        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        float walk = Mth.cos(limbSwing * 0.6662F) * 0.9F * limbSwingAmount;
        this.frontLeftLeg.xRot = walk;
        this.frontRightLeg.xRot = -walk;
        this.backLeftLeg.xRot = -walk;
        this.backRightLeg.xRot = walk;

        this.body.y += Mth.cos(limbSwing * 1.3324F) * 0.35F * limbSwingAmount;
        this.body.xRot = 0.03F + Mth.cos(ageInTicks * 0.05F) * 0.01F;

        this.tail.xRot = 0.15F + Mth.cos(ageInTicks * 0.08F) * 0.1F;
        this.tail.zRot = Mth.cos(ageInTicks * 0.055F) * 0.09F;
        this.tailTip.xRot = this.tail.xRot * 0.45F + 0.15F;
        this.tailTip.zRot = this.tail.zRot * 1.4F;

        if (entity.isInSittingPose()) {
            this.frontLeftLeg.visible = false;
            this.frontRightLeg.visible = false;
            this.backLeftLeg.visible = false;
            this.backRightLeg.visible = false;
            // The belly was raised so the paws show while standing. When the
            // legs are hidden for the loaf pose, drop the whole body back onto
            // the ground, otherwise the cat would float.
            this.body.y += 1.9F;
            this.head.y += 1.9F;
            this.tail.y += 1.9F;
            this.body.xRot = -0.04F;
            this.head.xRot = this.head.xRot * 0.7F + 0.05F;
            this.tail.xRot = 0.4F;
            this.tail.zRot = -0.55F;
            this.tailTip.xRot = 0.35F;
            this.tailTip.zRot = -0.45F;
        }
    }
}
