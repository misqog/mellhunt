package ru.misqog.mellhunt.client;

import ru.misqog.mellhunt.MellstroyMod;
import ru.misqog.mellhunt.casino.CasinoCashier;
import ru.misqog.mellhunt.network.ModNetwork;
import ru.misqog.mellhunt.registry.ModEntities;
import ru.misqog.mellhunt.world.MellstroyDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Lets the player open the cashier's counter from close range.
 *
 * <p>Right-clicking her is handled on the server; pressing E while standing at
 * the counter sends the same request, so both gestures work.</p>
 */
@Mod.EventBusSubscriber(modid = MellstroyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class CashierInteraction {

    /** How close the player has to stand to the counter (squared). */
    private static final double REACH_SQR = 25.0D;

    private static boolean enabled = true;

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!enabled || !event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (!MellstroyDimensions.isCasino(minecraft.level)) {
            return;
        }
        if (minecraft.hitResult instanceof EntityHitResult hit && isCashier(hit.getEntity())) {
            event.setCanceled(true);
            ModNetwork.sendCashierOpenRequest();
        }
    }

    /** Pressing E while standing at the counter opens it as well. */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!enabled || event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_E) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (!MellstroyDimensions.isCasino(minecraft.level)) {
            return;
        }
        if (minecraft.player.distanceToSqr(CasinoCashier.X, CasinoCashier.Y,
                CasinoCashier.Z) > REACH_SQR) {
            return;
        }
        ModNetwork.sendCashierOpenRequest();
    }

    private static boolean isCashier(Entity entity) {
        return entity != null && entity.getType() == ModEntities.CASHIER.get();
    }

    private CashierInteraction() {
    }
}
