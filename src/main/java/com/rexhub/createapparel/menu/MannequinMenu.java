package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class MannequinMenu extends ApparelMenu {
    private final Mannequin mannequin;

    public MannequinMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readVarInt()), false);
    }

    public MannequinMenu(int id, Inventory inventory, Mannequin mannequin) {
        this(id, inventory, mannequin, true);
    }

    private MannequinMenu(int id, Inventory inventory, Mannequin mannequin, boolean server) {
        super(ModMenuTypes.MANNEQUIN.get(), id, inventory,
                server ? mannequin.mannequinState().loadout() : ApparelLoadout.synchronizedView(mannequin.variant().form().slots()));
        this.mannequin = mannequin;
    }

    private static Mannequin find(Inventory inventory, int entityId) {
        if (inventory.player.level().getEntity(entityId) instanceof Mannequin mannequin) return mannequin;
        throw new IllegalStateException("Mannequin is not tracked: " + entityId);
    }

    public Mannequin mannequin() { return mannequin; }

    @Override
    public boolean stillValid(Player player) {
        return !mannequin.isRemoved() && mannequin.level() == player.level()
                && player.distanceToSqr(mannequin) <= 64
                && player.getAbilities().mayBuild && !player.isSpectator()
                && player.level().mayInteract(player, mannequin.blockPosition());
    }
}
