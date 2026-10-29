package ru.misqog.mellhunt.item;

import ru.misqog.mellhunt.block.LaptopBlock;
import ru.misqog.mellhunt.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An MSI gaming laptop.
 *
 * <p>A regular right-click on a block puts the laptop down; there is no
 * sneaking involved, unlike the chekushka. Once placed, right-clicking the
 * laptop turns it in 22.5 degree steps.</p>
 */
public class LaptopItem extends Item {

    public LaptopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Nothing happens in the air: the laptop is placed on a block.
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).canBeReplaced()
                ? clicked
                : clicked.relative(context.getClickedFace());

        if (!level.getBlockState(pos).canBeReplaced()
                || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, ModBlocks.LAPTOP.get().defaultBlockState()
                    .setValue(LaptopBlock.ROTATION, LaptopBlock.rotationFor(player)), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mellhunt.laptop.tooltip.place")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mellhunt.laptop.tooltip.rotate")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
