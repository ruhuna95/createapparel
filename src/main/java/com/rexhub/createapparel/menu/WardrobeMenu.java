package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.garment.GarmentSlot;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.block.WardrobeBlock;
import com.rexhub.createapparel.block.entity.WardrobeBlockEntity;
import com.rexhub.createapparel.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class WardrobeMenu extends ApparelMenu {
    private final BlockPos wardrobePos;
    private final WardrobeBlockEntity wardrobe;

    public WardrobeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(),
                ApparelLoadout.synchronizedView(java.util.EnumSet.allOf(GarmentSlot.class)), null);
    }

    public WardrobeMenu(int id, Inventory inventory, WardrobeBlockEntity wardrobe) {
        this(id, inventory, wardrobe.getBlockPos(), ApparelCapabilities.get(inventory.player)
                .orElseThrow(() -> new IllegalStateException("Player has no apparel inventory")), wardrobe);
        wardrobe.startOpen(inventory.player);
    }

    private WardrobeMenu(int id, Inventory inventory, BlockPos pos, ApparelLoadout loadout, WardrobeBlockEntity wardrobe) {
        super(ModMenuTypes.WARDROBE.get(), id, inventory, loadout);
        wardrobePos = pos.immutable();
        this.wardrobe = wardrobe;
    }

    public BlockPos wardrobePos() { return wardrobePos; }

    @Override
    protected Optional<GarmentSlot> quickMoveSlot(ItemStack stack) {
        return loadout.definition(stack).map(com.rexhub.createapparel.garment.GarmentDefinition::defaultSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(wardrobePos).getBlock() instanceof WardrobeBlock
                && player.distanceToSqr(wardrobePos.getX() + 0.5, wardrobePos.getY() + 0.5, wardrobePos.getZ() + 0.5) <= 64;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (wardrobe != null) wardrobe.stopOpen(player);
    }
}
