package com.rexhub.createapparel.item;

import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.entity.SilkwormState;
import com.rexhub.createapparel.client.renderer.SilkwormRearingTrayItemRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public final class SilkwormRearingTrayItem extends BlockItem {
    public SilkwormRearingTrayItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private SilkwormRearingTrayItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft minecraft = Minecraft.getInstance();
                    renderer = new SilkwormRearingTrayItemRenderer(minecraft.getBlockEntityRenderDispatcher(),
                            minecraft.getEntityModels());
                }
                return renderer;
            }
        });
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        CompoundTag stored = stack.getTagElement("BlockEntityTag");
        if (!(target instanceof Silkworm worm) || !worm.isAlive()
                || worm.getSilkwormState() != SilkwormState.LARVA
                || stored != null && stored.getCompound("TrayData").getBoolean("HasSilkworm")) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            ItemStack captured = stack.copyWithCount(1);
            CompoundTag data = captured.getOrCreateTagElement("BlockEntityTag").getCompound("TrayData");
            data.putBoolean("HasSilkworm", true);
            data.putString("State", "READY");
            captured.getOrCreateTagElement("BlockEntityTag").put("TrayData", data);
            stack.shrink(1);
            if (stack.isEmpty()) player.setItemInHand(hand, captured);
            else player.getInventory().placeItemBackInInventory(captured);
            worm.dropLeash(true, true);
            worm.discard();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag blockTag = stack.getTagElement("BlockEntityTag");
        if (blockTag != null && blockTag.getCompound("TrayData").getBoolean("HasSilkworm")) {
            tooltip.add(Component.translatable("tooltip.createapparel.rearing.silkworm",
                    Component.translatable("tooltip.createapparel.rearing.present")).withStyle(ChatFormatting.GRAY));
        }
    }
}
