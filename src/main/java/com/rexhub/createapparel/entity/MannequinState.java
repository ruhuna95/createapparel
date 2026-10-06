package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import net.minecraft.nbt.CompoundTag;

public final class MannequinState {
    private final ApparelLoadout loadout;
    private PosePreset pose;
    private Runnable changeListener = () -> { };
    private long revision;

    public MannequinState(MannequinForm form) {
        loadout = new ApparelLoadout(form.slots());
        pose = form.defaultPose();
        loadout.setChangeListener(this::changed);
    }

    public ApparelLoadout loadout() { return loadout; }
    public PosePreset pose() { return pose; }
    public long revision() { return revision; }
    public void setChangeListener(Runnable listener) { changeListener = listener; }

    public boolean setPose(PosePreset next, MannequinForm form) {
        if (!form.poses().contains(next)) return false;
        if (next != pose) {
            pose = next;
            changed();
        }
        return true;
    }

    private void changed() {
        revision++;
        changeListener.run();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("ApparelLoadout", loadout.serializeNBT());
        tag.putString("PosePreset", pose.id());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag, MannequinForm form) {
        loadout.deserializeNBT(tag.getCompound("ApparelLoadout"));
        pose = PosePreset.read(tag.getString("PosePreset"), form);
        revision++;
    }
}
