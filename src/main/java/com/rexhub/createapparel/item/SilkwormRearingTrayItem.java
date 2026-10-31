package com.rexhub.createapparel.item;

import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.entity.SilkwormState;
import net.minecraft.ChatFormatting;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public final class SilkwormRearingTrayItem extends BlockItem {
    public SilkwormRearingTrayItem(Block block, Properties properties) { super(block, properties); }



    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        CompoundTag stored = stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (!(target instanceof Silkworm worm) || !worm.isAlive()
                || worm.getSilkwormState() != SilkwormState.LARVA
                || stored != null && stored.getCompound("TrayData").getBoolean("HasSilkworm")) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            ItemStack captured = stack.copyWithCount(1);
            CompoundTag data = stored.getCompound("TrayData");
            data.putBoolean("HasSilkworm", true);
            data.putString("State", "READY");
            stored.put("TrayData", data);
            BlockItem.setBlockEntityData(captured, com.rexhub.createapparel.init.ModBlockEntities.SILKWORM_REARING_TRAY.get(), stored);
            stack.shrink(1);
            if (stack.isEmpty()) player.setItemInHand(hand, captured);
            else player.getInventory().placeItemBackInInventory(captured);
            worm.dropLeash(true, true);
            worm.discard();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var level = context.level();
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag blockTag = stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (blockTag != null && blockTag.getCompound("TrayData").getBoolean("HasSilkworm")) {
            tooltip.add(Component.translatable("tooltip.createapparel.rearing.silkworm",
                    Component.translatable("tooltip.createapparel.rearing.present")).withStyle(ChatFormatting.GRAY));
        }
    }
}
