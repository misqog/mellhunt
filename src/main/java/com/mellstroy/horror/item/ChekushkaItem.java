package com.mellstroy.horror.item;

import com.mellstroy.horror.entity.MellstroyEntity;
import com.mellstroy.horror.world.MellstroyHouseManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A bottle of the cheapest vodka in the world.
 *
 * <p>Two uses: throw it to Mellstroy and he leaves you alone for a few
 * minutes, or drink it yourself. The drink gives nausea and Strength II for
 * ten seconds and, more importantly, makes him lose your trail for exactly
 * that long.</p>
 */
public class ChekushkaItem extends Item {

    private static final int DRINK_TICKS = 32;
    /** Nausea, Strength II and Mellstroy losing the player all last 10 s. */
    private static final int EFFECT_TICKS = 10 * 20;
    /** He only loses the player if he was close enough to be following them. */
    private static final double LOSE_RADIUS = 128.0D;

    public ChekushkaItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_TICKS, 1));

            // The player disappears from his radar for ten seconds, even if he
            // was already rushing them.
            MellstroyHouseManager.stopRush(player.getUUID());
            for (MellstroyEntity mob : player.serverLevel().getEntitiesOfClass(MellstroyEntity.class,
                    player.getBoundingBox().inflate(LOSE_RADIUS))) {
                mob.giveGrace(EFFECT_TICKS);
            }

            player.displayClientMessage(Component.translatable("message.mellstroy.chekushka.drunk")
                    .withStyle(ChatFormatting.GOLD), true);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mellstroy.chekushka.tooltip.give")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mellstroy.chekushka.tooltip.drink")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
