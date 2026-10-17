package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.client.garment.GarmentRenderSource;
import com.rexhub.createapparel.apparel.ApparelBodyMode;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ResolvedApparelState;
import com.rexhub.createapparel.client.garment.FirstPersonApparelState;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.GarmentWearContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApparelRenderSnapshot implements GarmentRenderSource {
    private final int playerId;
    private final long revision;
    private final List<ItemStack> contents;
    private final Map<GarmentSlot, EquippedStyleState> styles;
    private final ArmProfile armProfile;
    private final ChestProfile chestProfile;
    private final SkinTone skinTone;
    private final boolean bodyMode;
    private final boolean armorHidden;
    private final ResolvedApparelState resolved = new ResolvedApparelState(this::resolve);
    private final FirstPersonApparelState firstPerson = new FirstPersonApparelState(this::garment, this::resolveBody);

    ApparelRenderSnapshot(int playerId, long revision, List<ItemStack> contents,
                          Map<GarmentSlot, EquippedStyleState> styles, boolean forceSkin, boolean forceHideArmor,
                          ArmProfile armProfile, ChestProfile chestProfile, SkinTone skinTone) {
        this.playerId = playerId;
        this.revision = revision;
        this.contents = contents.stream().map(ItemStack::copy).toList();
        this.styles = Map.copyOf(styles);
        this.armProfile = armProfile;
        this.chestProfile = chestProfile;
        this.skinTone = skinTone;
        boolean active = ApparelBodyMode.isActive(this.contents);
        bodyMode = forceSkin || active;
        armorHidden = forceHideArmor || active;
    }

    public Optional<ResolvedGarment> garment(GarmentSlot slot) {
        return resolved.garment(ClientGarmentAssets.INSTANCE.generation(), slot);
    }

    private Optional<ResolvedGarment> resolve(GarmentSlot slot) {
        ItemStack stack = contents.get(slot.ordinal());
        return stack.isEmpty() ? Optional.empty() : ClientGarmentAssets.INSTANCE.resolve(stack,
                    new GarmentWearContext(slot, styles.getOrDefault(slot, EquippedStyleState.EMPTY),
                            !isEmpty(GarmentSlot.COAT)), armProfile, chestProfile);
    }

    public ResourceLocation texture(ResolvedPart part) {
        return resolved.texture(part);
    }

    private Optional<List<ClientGarmentAssets.ResolvedSupportPart>> resolveBody() {
        return bodyMode ? ClientGarmentAssets.INSTANCE.resolveBody(armProfile, chestProfile) : Optional.empty();
    }

    public FirstPersonApparelState.ArmParts firstPersonArm(HumanoidArm arm) {
        return firstPerson.arm(ClientGarmentAssets.INSTANCE.generation(), arm);
    }

    public boolean isEmpty(GarmentSlot slot) { return contents.get(slot.ordinal()).isEmpty(); }
    public boolean matches(GarmentSlot slot, ItemStack stack) { return ItemStack.matches(contents.get(slot.ordinal()), stack); }
    public ItemStack stackCopy(GarmentSlot slot) { return contents.get(slot.ordinal()).copy(); }
    public int playerId() { return playerId; }
    public long revision() { return revision; }
    public Map<GarmentSlot, EquippedStyleState> styles() { return styles; }
    public ArmProfile armProfile() { return armProfile; }
    public ChestProfile chestProfile() { return chestProfile; }
    public SkinTone skinTone() { return skinTone; }
    public boolean bodyMode() { return bodyMode; }
    public boolean armorHidden() { return armorHidden; }
}
