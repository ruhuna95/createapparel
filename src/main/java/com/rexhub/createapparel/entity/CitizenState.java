package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.citizen.CitizenLoadout;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;

public final class CitizenState {
    private final ApparelLoadout loadout = new CitizenLoadout();
    private CitizenAppearance appearance = CitizenAppearance.DEFAULT;
    private CitizenAge age = CitizenAge.ADULT;
    private CitizenBehaviorState behavior = CitizenBehaviorState.DEFAULT;
    private com.rexhub.createapparel.citizen.population.CitizenPopulationState population = com.rexhub.createapparel.citizen.population.CitizenPopulationState.DEFAULT;
    private Runnable changeListener = () -> { };
    private long revision;

    public CitizenState() { loadout.setChangeListener(this::changed); }

    public ApparelLoadout loadout() { return loadout; }
    public CitizenAppearance appearance() { return appearance; }
    public CitizenAge age() { return age; }
    public CitizenBehaviorState behavior() { return behavior; }
    public com.rexhub.createapparel.citizen.population.CitizenPopulationState population() { return population; }
    public void setPopulation(com.rexhub.createapparel.citizen.population.CitizenPopulationState next) {
        if (!population.equals(Objects.requireNonNull(next))) { population = next; changed(); }
    }
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

    public void setBehavior(CitizenBehaviorState next) {
        if (!behavior.equals(Objects.requireNonNull(next))) {
            behavior = next;
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
        behavior.write(tag);
        tag.put("Population", population.save());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        appearance = CitizenAppearance.read(tag);
        age = CitizenAge.read(tag.getString("Age"));
        behavior = CitizenBehaviorState.read(tag);
        population = com.rexhub.createapparel.citizen.population.CitizenPopulationState.load(tag.getCompound("Population"));
        if (tag.contains("ApparelSlots", CompoundTag.TAG_COMPOUND)) {
            loadout.deserializeNBT(tag.getCompound("ApparelSlots"));
        }
        revision++;
    }
}
