package com.rexhub.createapparel.item;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.menu.CitizenMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

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
        if (!(event.getEntity().getItemInHand(event.getHand()).getItem() instanceof SmartPhoneItem)) return;
        if (event.getTarget() instanceof Citizen citizen) {
            event.setCanceled(true);
            if (event.getLevel().isClientSide) {
                event.setCancellationResult(InteractionResult.SUCCESS);
            } else if (event.getEntity() instanceof ServerPlayer player && CitizenMenu.canEdit(player, citizen)) {
                NetworkHooks.openScreen(player,
                        new SimpleMenuProvider((id, inventory, viewer) -> new CitizenMenu(id, inventory, citizen),
                                Component.translatable("container.createapparel.citizen")),
                        buffer -> buffer.writeVarInt(citizen.getId()));
                event.setCancellationResult(InteractionResult.CONSUME);
            } else event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
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
