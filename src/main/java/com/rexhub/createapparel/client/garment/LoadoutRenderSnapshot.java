package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.GarmentWearContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LoadoutRenderSnapshot implements GarmentRenderSource {
    private final List<ItemStack> contents;
    private final Map<GarmentSlot, EquippedStyleState> styles;
    private final Set<GarmentSlot> slots;
    private final ArmProfile armProfile;
    private final ChestProfile chestProfile;
    private final ResolvedApparelState resolved = new ResolvedApparelState(this::resolve);

    public LoadoutRenderSnapshot(ApparelLoadout loadout, ArmProfile armProfile, ChestProfile chestProfile) {
        contents = loadout.copyContents();
        styles = loadout.copyStyles();
        slots = java.util.Arrays.stream(GarmentSlot.values()).filter(loadout::supports)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.armProfile = armProfile;
        this.chestProfile = chestProfile;
    }

    private Optional<ClientGarmentAssets.ResolvedGarment> resolve(GarmentSlot slot) {
        ItemStack stack = contents.get(slot.ordinal());
        return stack.isEmpty() || !slots.contains(slot) ? Optional.empty()
                : ClientGarmentAssets.INSTANCE.resolve(stack,
                new GarmentWearContext(slot, styles.getOrDefault(slot, EquippedStyleState.EMPTY),
                        !contents.get(GarmentSlot.COAT.ordinal()).isEmpty()),
                armProfile, chestProfile);
    }

    @Override
    public Optional<ClientGarmentAssets.ResolvedGarment> garment(GarmentSlot slot) {
        return resolved.garment(ClientGarmentAssets.INSTANCE.generation(), slot);
    }

    @Override
    public ResourceLocation texture(ClientGarmentAssets.ResolvedPart part) {
        return resolved.texture(part);
    }

    @Override
    public ArmProfile armProfile() { return armProfile; }

    @Override
    public ChestProfile chestProfile() { return chestProfile; }
}
