package com.rexhub.createapparel.item;

import com.rexhub.createapparel.citizen.CitizenConversion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;
import java.util.List;

public final class SmartPhoneItem extends Item {
    public SmartPhoneItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.createapparel.smart_phone.convert_villager").withStyle(ChatFormatting.GRAY));
    }

    public void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)
                || villager.getType() != net.minecraft.world.entity.EntityType.VILLAGER) return;
        event.setCanceled(true);
        if (event.getLevel().isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        var result = CitizenConversion.convert(villager);
        event.setCancellationResult(result == CitizenConversion.Result.SUCCESS ? InteractionResult.CONSUME : InteractionResult.FAIL);
        if (result == CitizenConversion.Result.FAILED) {
            event.getEntity().displayClientMessage(Component.translatable("message.createapparel.citizen_conversion_failed"), true);
        }
    }
}
