package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import javax.annotation.Nullable;

public final class RetailPurchase {
    public record Candidate(IItemHandler storage, int index, GarmentSlot slot, ItemStack stack) { }
    private RetailPurchase() { }
    @Nullable public static Candidate select(Citizen citizen, CashRegisterBlockEntity register, @Nullable GarmentSlot requiredSlot, boolean exact) {
        Candidate selected = null; double total = 0;
        var demand = FashionEvaluator.demand(citizen);
        for (var storage : register.storages()) {
            for (int index = 0; index < storage.getSlots(); index++) {
                ItemStack stack = storage.getStackInSlot(index);
                if (stack.isEmpty() || exact && !ItemStack.isSameItemSameTags(stack, citizen.citizenState().fashion().desiredGarment())) continue;
                var definition = citizen.citizenState().loadout().definition(stack).orElse(null);
                if (definition == null) continue;
                var slot = requiredSlot == null ? definition.defaultSlot() : requiredSlot;
                if (!FashionEvaluator.eligible(citizen, stack, slot)) continue;
                ItemStack simulated = storage.extractItem(index, 1, true);
                if (simulated.getCount() != 1 || !ItemStack.isSameItemSameTags(simulated, stack)) continue;
                double weight = FashionEvaluator.attractiveness(simulated, demand);
                total += weight;
                if (citizen.getRandom().nextDouble() * total < weight) selected = new Candidate(storage, index, slot, simulated.copy());
            }
        }
        return selected;
    }
    public static boolean purchase(Citizen citizen, CashRegisterBlockEntity register, Candidate candidate) {
        if (!register.isOpen() || !FashionEvaluator.eligible(citizen, candidate.stack(), candidate.slot())) return false;
        var simulated = candidate.storage().extractItem(candidate.index(), 1, true);
        if (simulated.getCount() != 1 || !ItemStack.isSameItemSameTags(simulated, candidate.stack())) return false;
        var extracted = candidate.storage().extractItem(candidate.index(), 1, false);
        if (extracted.getCount() != 1 || !ItemStack.isSameItemSameTags(extracted, candidate.stack())) {
            if (!extracted.isEmpty()) {
                var remainder = ItemHandlerHelper.insertItemStacked(candidate.storage(), extracted, false);
                if (!remainder.isEmpty()) net.minecraft.world.Containers.dropItemStack(citizen.level(), register.getBlockPos().getX() + 0.5,
                        register.getBlockPos().getY() + 1, register.getBlockPos().getZ() + 0.5, remainder);
            }
            return false;
        }
        citizen.citizenState().loadout().setStackInSlot(candidate.slot().ordinal(), extracted);
        citizen.citizenState().fashion().purchased(candidate.slot(), register.reference());
        citizen.syncState(); return true;
    }
}
