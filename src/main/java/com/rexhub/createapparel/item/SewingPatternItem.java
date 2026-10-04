package com.rexhub.createapparel.item;

import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class SewingPatternItem extends Item {
    public SewingPatternItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.openSewingPatternEditor(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, level, tooltip, flag);
        PatternData.fromStack(stack).ifPresentOrElse(data -> {
            tooltip.add(Component.translatable(
                    "tooltip.createapparel.sewing_pattern.recipe",
                    getConfiguredGarmentName(stack, level)
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.createapparel.sewing_pattern.design",
                    Component.translatable("design.createapparel." + data.design())
            ).withStyle(ChatFormatting.GRAY));
            data.secondaryColor().ifPresent(color -> tooltip.add(Component.translatable(
                    "tooltip.createapparel.sewing_pattern.secondary_color",
                    Component.translatable(color.translationKey())
            ).withStyle(ChatFormatting.GRAY)));
            data.accentColor().ifPresent(color -> tooltip.add(Component.translatable(
                    "tooltip.createapparel.sewing_pattern.accent_color",
                    Component.translatable(color.translationKey())
            ).withStyle(ChatFormatting.GRAY)));
        }, () -> tooltip.add(Component.translatable(
                "tooltip.createapparel.sewing_pattern.unset"
        ).withStyle(ChatFormatting.GRAY)));
        if (stack.getTagElement(PatternData.TAG_NAME) != null) {
            tooltip.add(Component.translatable("itemGroup.createapparel")
                    .withStyle(ChatFormatting.BLUE));
        }
    }

    public static Component getConfiguredGarmentName(ItemStack stack, @Nullable Level level) {
        if (level == null) {
            return Component.translatable("tooltip.createapparel.sewing_pattern.unknown_garment");
        }
        return PatternData.fromStack(stack)
                .flatMap(data -> level.getRecipeManager().byKey(data.recipe()))
                .filter(SewingRecipe.class::isInstance)
                .map(SewingRecipe.class::cast)
                .map(recipe -> recipe.getResultItem(level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::getHoverName)
                .orElseGet(() -> Component.translatable(
                        "tooltip.createapparel.sewing_pattern.unknown_garment"
                ));
    }
}
