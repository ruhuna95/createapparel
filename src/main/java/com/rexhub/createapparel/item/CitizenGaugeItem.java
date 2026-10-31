package com.rexhub.createapparel.item;

import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

public final class CitizenGaugeItem extends BlockItem {
    private static final String CENTER_RESERVATION = "GaugeCenterReservation";
    public CitizenGaugeItem(Block block, Properties properties) { super(block, properties); }
    @Override public int getMaxStackSize(ItemStack stack) {
        return com.rexhub.createapparel.item.ItemData.hasData(stack) && com.rexhub.createapparel.item.ItemData.read(stack).contains("GaugeCenter", net.minecraft.nbt.Tag.TAG_LONG) ? 1 : super.getMaxStackSize(stack);
    }
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !(entity instanceof net.minecraft.world.entity.player.Player player)
                || Math.floorMod(level.getGameTime() + entity.getId(), 200) != 0) return;
        var id = reservation(stack); var center = center(stack, level); if (id == null || center == null) return;
        int observed = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            var candidate = player.getInventory().getItem(index); if (id.equals(reservation(candidate))) observed += candidate.getCount();
        }
        CitizenPopulationManager.get(server).data().verifyCenter(id, level.dimension().location().toString(), observed, level.getGameTime());
    }
    @Override public boolean isFoil(ItemStack stack) {
        return com.rexhub.createapparel.item.ItemData.hasData(stack) && com.rexhub.createapparel.item.ItemData.read(stack).contains("GaugeCenter", net.minecraft.nbt.Tag.TAG_LONG) || super.isFoil(stack);
    }
    @Nullable public static BlockPos center(ItemStack stack, Level level) {
        var tag = com.rexhub.createapparel.item.ItemData.read(stack);
        return tag != null && tag.contains("GaugeCenter", net.minecraft.nbt.Tag.TAG_LONG)
                && tag.getString("GaugeDimension").equals(level.dimension().location().toString()) ? BlockPos.of(tag.getLong("GaugeCenter")) : null;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()) return InteractionResult.FAIL;
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CitizenGaugeBlockEntity)
            return com.rexhub.createapparel.citizen.population.GaugeInteractions.use(context.getLevel(), context.getClickedPos(), player, context.getHand(),
                    new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
        if (player.isShiftKeyDown() && context.getClickedFace() == net.minecraft.core.Direction.UP) {
            if (context.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
                var stack = context.getItemInHand();
                var previous = reservation(stack);
                var data = CitizenPopulationManager.get(server).data();
                var id = data.reserveCenter(context.getClickedPos(), previous, 1, server.dimension().location().toString(), server.getGameTime());
                if (id == null) { reportCenterUsed(player); return InteractionResult.FAIL; }
                if (!id.equals(previous)) releasePreviousCenter(server, stack, previous, false);
                else if (stack.getCount() > 1) {
                    data.verifyCenter(previous, server.dimension().location().toString(), stack.getCount(), server.getGameTime());
                    data.releaseCenter(previous, stack.getCount() - 1);
                }
                var remainder = stack.split(stack.getCount() - 1);
                if (com.rexhub.createapparel.item.ItemData.hasData(remainder)) {
                    var remainderTag = com.rexhub.createapparel.item.ItemData.read(remainder);
                    remainderTag.remove("GaugeCenter"); remainderTag.remove("GaugeDimension"); remainderTag.remove(CENTER_RESERVATION);
                    com.rexhub.createapparel.item.ItemData.write(remainder, remainderTag);
                }
                var tag = com.rexhub.createapparel.item.ItemData.readOrEmpty(stack);
                tag.putLong("GaugeCenter", context.getClickedPos().asLong());
                tag.putString("GaugeDimension", context.getLevel().dimension().location().toString());
                tag.putUUID(CENTER_RESERVATION, id);
                tag.remove("GaugeOutflow");
                com.rexhub.createapparel.item.ItemData.write(stack, tag);
                if (player.isCreative()) player.setItemInHand(context.getHand(), stack.copy());
                player.getInventory().placeItemBackInInventory(remainder);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                player.displayClientMessage(Component.translatable("message.createapparel.gauge.center", context.getClickedPos().toShortString()), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        var tag = com.rexhub.createapparel.item.ItemData.read(context.getItemInHand());
        if (tag != null && tag.contains("GaugeCenter") && center(context.getItemInHand(), context.getLevel()) == null) {
            if (!context.getLevel().isClientSide) player.displayClientMessage(Component.translatable("message.createapparel.gauge.dimension"), true);
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
    @Override public InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context) {
        var level = context.getLevel(); var pos = context.getClickedPos();
        var center = center(context.getItemInHand(), level);
        if (center != null && level instanceof net.minecraft.server.level.ServerLevel server
                && !CitizenPopulationManager.get(server).data().centerAvailable(center, reservation(context.getItemInHand()))) {
            reportCenterUsed(context.getPlayer()); return InteractionResult.FAIL;
        }
        boolean outflow = com.rexhub.createapparel.item.ItemData.hasData(context.getItemInHand()) && com.rexhub.createapparel.item.ItemData.read(context.getItemInHand()).getBoolean("GaugeOutflow");
        if (level.getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity) {
            var player = context.getPlayer();
            if (player == null || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())
                    || player instanceof net.minecraft.server.level.ServerPlayer server && !com.rexhub.createapparel.citizen.population.GaugeInteractions.protectedEdit(server, pos)) return InteractionResult.FAIL;
            int slot = com.rexhub.createapparel.block.CitizenGaugeBlock.targetedSlot(pos, entity.getBlockState(), context.getClickLocation());
            if (!entity.initialize(slot, center, outflow)) return InteractionResult.FAIL;
            if (!player.isCreative()) context.getItemInHand().shrink(1);
            level.playSound(player, pos, net.minecraft.sounds.SoundEvents.METAL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        var result = super.place(context);
        if (result.consumesAction() && level.getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity)
            entity.initialize(com.rexhub.createapparel.block.CitizenGaugeBlock.targetedSlot(pos, entity.getBlockState(), context.getClickLocation()), center, outflow);
        return result;
    }
    @Nullable private static java.util.UUID reservation(ItemStack stack) {
        var tag = com.rexhub.createapparel.item.ItemData.read(stack);
        return tag != null && tag.hasUUID(CENTER_RESERVATION) ? tag.getUUID(CENTER_RESERVATION) : null;
    }
    private static void releasePreviousCenter(net.minecraft.server.level.ServerLevel level, ItemStack stack, @Nullable java.util.UUID reservation, boolean destroyed) {
        if (reservation == null || com.rexhub.createapparel.item.ItemData.read(stack) == null) return;
        var dimension = net.minecraft.resources.ResourceLocation.tryParse(com.rexhub.createapparel.item.ItemData.read(stack).getString("GaugeDimension"));
        if (dimension == null) return;
        var previous = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        if (previous != null) {
            var data = CitizenPopulationManager.get(previous).data();
            if (destroyed) data.releaseCenter(reservation, stack.getCount());
            else {
                data.verifyCenter(reservation, previous.dimension().location().toString(), stack.getCount(), previous.getGameTime());
                data.releaseCenterForRerecord(reservation, stack.getCount());
            }
        }
    }
    public static void releaseDestroyedReservation(net.minecraft.world.entity.item.ItemEntity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel server) || !(entity.getItem().getItem() instanceof CitizenGaugeItem)) return;
        var reservation = reservation(entity.getItem());
        if (reservation == null) return;
        releasePreviousCenter(server, entity.getItem(), reservation, true);
        com.rexhub.createapparel.item.ItemData.update(entity.getItem(), customData -> customData.remove(CENTER_RESERVATION));
    }
    public static void releaseDestroyedReservation(net.minecraft.server.level.ServerLevel level, ItemStack stack) {
        if (stack.getItem() instanceof CitizenGaugeItem) releasePreviousCenter(level, stack, reservation(stack), true);
    }
    public static void releaseCraftedReservation(net.minecraft.server.level.ServerLevel level, ItemStack stack) {
        if (stack.getItem() instanceof CitizenGaugeItem) releasePreviousCenter(level, stack.copyWithCount(1), reservation(stack), false);
    }
    @Override public void onDestroyed(net.minecraft.world.entity.item.ItemEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        super.onDestroyed(entity, source);
        releaseDestroyedReservation(entity);
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        var level = context.level();
        var tag = com.rexhub.createapparel.item.ItemData.read(stack);
        if (tag != null && tag.contains("GaugeCenter", net.minecraft.nbt.Tag.TAG_LONG))
            tooltip.add(Component.translatable("message.createapparel.gauge.center", BlockPos.of(tag.getLong("GaugeCenter")).toShortString()));
    }
    private static void reportCenterUsed(@Nullable net.minecraft.world.entity.player.Player player) {
        if (player != null) player.displayClientMessage(Component.translatable("message.createapparel.gauge.center_used").withStyle(net.minecraft.ChatFormatting.RED), true);
    }
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        return false;
    }
}
