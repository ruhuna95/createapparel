package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import net.minecraft.nbt.CompoundTag;

public final class MannequinState {
    public static final int ROTATION_STEPS = 16;
    private final ApparelLoadout loadout;
    private PosePreset pose;
    private int bodyRotationStep;
    private Runnable changeListener = () -> { };
    private long revision;
    @javax.annotation.Nullable private com.rexhub.createapparel.fashion.PurchasedRegisterRef linkedRegister;
    @javax.annotation.Nullable public com.rexhub.createapparel.fashion.PurchasedRegisterRef linkedRegister() { return linkedRegister; }
    public void setLinkedRegister(@javax.annotation.Nullable com.rexhub.createapparel.fashion.PurchasedRegisterRef reference) {
        linkedRegister = reference; changed();
    }

    public MannequinState(MannequinForm form) {
        loadout = new ApparelLoadout(form.slots());
        pose = form.defaultPose();
        loadout.setChangeListener(this::changed);
    }

    public ApparelLoadout loadout() { return loadout; }
    public PosePreset pose() { return pose; }
    public int bodyRotationStep() { return bodyRotationStep; }
    public float bodyRotationDegrees() { return bodyRotationStep * 22.5F; }
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

    public boolean setBodyRotationStep(int step) {
        if (step < 0 || step >= ROTATION_STEPS) return false;
        if (step != bodyRotationStep) {
            bodyRotationStep = step;
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
        tag.putInt("BodyRotationStep", bodyRotationStep);
        if (linkedRegister != null) tag.put("LinkedRegister", linkedRegister.save());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag, MannequinForm form) {
        loadout.deserializeNBT(tag.getCompound("ApparelLoadout"));
        pose = PosePreset.read(tag.getString("PosePreset"), form);
        int step = tag.getInt("BodyRotationStep");
        bodyRotationStep = step >= 0 && step < ROTATION_STEPS ? step : 0;
        linkedRegister = com.rexhub.createapparel.fashion.PurchasedRegisterRef.load(tag.getCompound("LinkedRegister"));
        revision++;
    }
}
