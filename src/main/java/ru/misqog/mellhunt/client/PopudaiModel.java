package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.entity.PopudaiEntity;
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
 * Popudai in the pose from the reference render: big head on top/front, yellow
 * chest, blue wing, small legs and the existing rear appendage.
 *
 * <p>The face layer and the rear appendage are deliberately unchanged.</p>
 */
public class PopudaiModel<T extends PopudaiEntity> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(MellstroyMod.MOD_ID, "popudai"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftFeather;
    private final ModelPart rightFeather;
    private final ModelPart rearBase;
    private final ModelPart rearMid;
    private final ModelPart rearTip;
    private final ModelPart rearBulb;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public PopudaiModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.leftFeather = root.getChild("left_feather");
        this.rightFeather = root.getChild("right_feather");
        this.rearBase = root.getChild("rear_base");
        this.rearMid = this.rearBase.getChild("rear_mid");
        this.rearTip = this.rearMid.getChild("rear_tip");
        this.rearBulb = this.rearTip.getChild("rear_bulb");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-7.0F, -6.0F, -9.0F, 14.0F, 12.0F, 18.0F),
                PartPose.offset(0.0F, 13.0F, 0.0F));

        // Big head on top/front; face layer untouched.
        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F),
                PartPose.offset(0.0F, 4.0F, -6.0F));

        root.addOrReplaceChild("left_wing",
                CubeListBuilder.create()
                        .texOffs(64, 0).addBox(0.0F, -4.5F, -6.0F, 3.0F, 9.0F, 12.0F),
                PartPose.offset(-8.5F, 13.0F, 0.0F));
        root.addOrReplaceChild("right_wing",
                CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-3.0F, -4.5F, -6.0F, 3.0F, 9.0F, 12.0F),
                PartPose.offset(8.5F, 13.0F, 0.0F));

        root.addOrReplaceChild("left_feather",
                CubeListBuilder.create()
                        .texOffs(64, 32).addBox(0.0F, -2.5F, -4.5F, 1.0F, 5.0F, 9.0F),
                PartPose.offset(-9.5F, 12.0F, 1.0F));
        root.addOrReplaceChild("right_feather",
                CubeListBuilder.create()
                        .texOffs(64, 32).addBox(-1.0F, -2.5F, -4.5F, 1.0F, 5.0F, 9.0F),
                PartPose.offset(9.5F, 12.0F, 1.0F));

        // Rear appendage is exactly the previous one.
        PartDefinition rearBase = root.addOrReplaceChild("rear_base",
                CubeListBuilder.create()
                        .texOffs(96, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 13.0F, 9.0F));
        PartDefinition rearMid = rearBase.addOrReplaceChild("rear_mid",
                CubeListBuilder.create()
                        .texOffs(96, 16).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        PartDefinition rearTip = rearMid.addOrReplaceChild("rear_tip",
                CubeListBuilder.create()
                        .texOffs(96, 32).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 5.0F),
                PartPose.offset(0.0F, 0.0F, 6.0F));
        rearTip.addOrReplaceChild("rear_bulb",
                CubeListBuilder.create()
                        .texOffs(96, 48).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 5.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(96, 64).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(-3.0F, 19.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(96, 64).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(3.0F, 19.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    public ModelPart headPart() {
        return this.head;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.leftLeg.visible = true;
        this.rightLeg.visible = true;

        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        float walk = Mth.cos(limbSwing * 0.6662F) * 0.8F * limbSwingAmount;
        this.leftLeg.xRot = walk;
        this.rightLeg.xRot = -walk;

        this.body.y += Mth.cos(limbSwing * 1.3324F) * 0.3F * limbSwingAmount;
        this.body.xRot = 0.02F + Mth.cos(ageInTicks * 0.05F) * 0.012F;

        float flap = Mth.cos(ageInTicks * 0.32F) * 0.14F
                + Mth.cos(limbSwing * 0.7F) * 0.26F * limbSwingAmount;
        this.leftWing.zRot = 0.10F + flap;
        this.rightWing.zRot = -0.10F - flap;
        this.leftFeather.zRot = 0.20F + flap * 1.1F;
        this.rightFeather.zRot = -0.20F - flap * 1.1F;

        float sway = Mth.cos(ageInTicks * 0.06F) * 0.07F;
        this.rearBase.xRot = -0.05F + sway * 0.5F;
        this.rearBase.zRot = sway * 0.6F;
        this.rearMid.xRot = 0.04F + sway * 0.5F;
        this.rearMid.zRot = sway * 0.4F;
        this.rearTip.xRot = 0.03F + sway * 0.4F;
        this.rearBulb.xRot = sway * 0.3F;

        if (entity.isInSittingPose()) {
            this.leftLeg.visible = false;
            this.rightLeg.visible = false;

            this.body.y += 5.5F;
            this.head.y += 5.5F;
            this.leftWing.y += 5.5F;
            this.rightWing.y += 5.5F;
            this.leftFeather.y += 5.5F;
            this.rightFeather.y += 5.5F;
            this.rearBase.y += 5.5F;

            this.leftWing.zRot = 1.30F;
            this.rightWing.zRot = -1.30F;
            this.leftFeather.zRot = 1.10F;
            this.rightFeather.zRot = -1.10F;
            this.rearBase.xRot = -0.40F;
            this.rearBase.zRot = 0.0F;
            this.rearMid.xRot = 0.12F;
            this.rearMid.zRot = 0.0F;
            this.rearTip.xRot = 0.10F;
            this.rearBulb.xRot = 0.0F;
            this.body.xRot = -0.03F;
            this.head.xRot = this.head.xRot * 0.65F + 0.08F;
        }
    }
}
