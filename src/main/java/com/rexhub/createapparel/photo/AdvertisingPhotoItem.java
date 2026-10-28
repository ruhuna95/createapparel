package com.rexhub.createapparel.photo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AdvertisingPhotoItem extends Item {
    public AdvertisingPhotoItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var photo = AdvertisingPhotoData.read(player.getItemInHand(hand));
        if (photo == null) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (player instanceof ServerPlayer server) PhotographyMenu.openEdit(server, photo, hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
