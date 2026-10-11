package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;

public final class CitizenState {
    private final ApparelLoadout loadout = new ApparelLoadout();
    private CitizenAppearance appearance = CitizenAppearance.DEFAULT;
    private CitizenAge age = CitizenAge.ADULT;
    private Runnable changeListener = () -> { };
    private long revision;

    public CitizenState() { loadout.setChangeListener(this::changed); }

    public ApparelLoadout loadout() { return loadout; }
    public CitizenAppearance appearance() { return appearance; }
    public CitizenAge age() { return age; }
    public long revision() { return revision; }
    public void setChangeListener(Runnable listener) { changeListener = Objects.requireNonNull(listener); }

    public void setAppearance(CitizenAppearance next) {
        if (!appearance.equals(Objects.requireNonNull(next))) {
            appearance = next;
            changed();
        }
    }

    public void setAge(CitizenAge next) {
        if (age != Objects.requireNonNull(next)) {
            age = next;
            changed();
        }
    }

    private void changed() {
        revision++;
        changeListener.run();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        appearance.write(tag);
        tag.putString("Age", age.getSerializedName());
        tag.put("ApparelSlots", loadout.serializeNBT());
        tag.putString("BaseMode", "stand");
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        appearance = CitizenAppearance.read(tag);
        age = CitizenAge.read(tag.getString("Age"));
        if (tag.contains("ApparelSlots", CompoundTag.TAG_COMPOUND)) {
            loadout.deserializeNBT(tag.getCompound("ApparelSlots"));
        }
        revision++;
    }
}
