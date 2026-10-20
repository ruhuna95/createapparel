package com.rexhub.createapparel.client.mixin;

import com.rexhub.createapparel.network.GaugeReservationDiscardPacket;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryGaugeMixin {
    @Accessor("destroyItemSlot") @Nullable protected abstract Slot createapparel$destroyItemSlot();
    @Invoker("isCreativeSlot") protected abstract boolean createapparel$isCreativeSlot(@Nullable Slot slot);
    @Unique private final List<GaugeReservationDiscardPacket> createapparel$discardedGauges = new ArrayList<>();
    @Unique private boolean createapparel$paletteDiscard;

    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void createapparel$captureDiscard(@Nullable Slot slot, int slotId, int button, ClickType type, CallbackInfo callback) {
        createapparel$discardedGauges.clear();
        createapparel$paletteDiscard = false;
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isCreative() || slot == null || !slot.mayPickup(player)) return;
        var screen = (CreativeModeInventoryScreen) (Object) this;
        if (!screen.getMenu().slots.contains(slot)) return;
        var trash = createapparel$destroyItemSlot();
        createapparel$paletteDiscard = slot != trash && createapparel$isCreativeSlot(slot) && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE);
        if (slot != trash && !createapparel$paletteDiscard) return;
        var stacks = !createapparel$paletteDiscard && type == ClickType.QUICK_MOVE
                ? player.inventoryMenu.getItems() : List.of(screen.getMenu().getCarried());
        for (var stack : stacks) {
            var packet = GaugeReservationDiscardPacket.fromStack(stack);
            if (packet != null) createapparel$discardedGauges.add(packet);
        }
    }

    @Inject(method = "slotClicked", at = @At("RETURN"))
    private void createapparel$releaseDiscarded(@Nullable Slot slot, int slotId, int button, ClickType type, CallbackInfo callback) {
        var screen = (CreativeModeInventoryScreen) (Object) this;
        if (createapparel$paletteDiscard) {
            var remaining = GaugeReservationDiscardPacket.fromStack(screen.getMenu().getCarried());
            for (var previous : createapparel$discardedGauges) {
                int retained = remaining != null && remaining.dimension().equals(previous.dimension())
                        && remaining.reservation().equals(previous.reservation()) ? remaining.count() : 0;
                int removed = previous.count() - retained;
                if (removed > 0) ModNetwork.discardGaugeReservation(new GaugeReservationDiscardPacket(previous.dimension(), previous.reservation(), removed));
            }
        } else if (type == ClickType.QUICK_MOVE || screen.getMenu().getCarried().isEmpty())
            createapparel$discardedGauges.forEach(ModNetwork::discardGaugeReservation);
        createapparel$discardedGauges.clear();
        createapparel$paletteDiscard = false;
    }
}
