package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.nbt.CompoundTag;

public record PhotoLayout(FabricColor background, Framing framing, Aspect aspect, Horizontal horizontal, Vertical vertical, boolean mirror, int bodyYaw) {
    public PhotoLayout {
        if (bodyYaw < 0 || bodyYaw >= 360) throw new IllegalArgumentException("Invalid photo body rotation");
    }
    public PhotoLayout(FabricColor background, Framing framing, Aspect aspect, Horizontal horizontal, Vertical vertical, boolean mirror) {
        this(background, framing, aspect, horizontal, vertical, mirror, 0);
    }
    public enum Framing { FULL_BODY, UPPER_BODY, CLOSE_UP }
    public enum Aspect { FIT, FILL }
    public enum Horizontal { LEFT, CENTER, RIGHT }
    public enum Vertical { UP, CENTER, DOWN }
    public static final PhotoLayout DEFAULT = new PhotoLayout(FabricColor.WHITE, Framing.FULL_BODY, Aspect.FIT, Horizontal.CENTER, Vertical.CENTER, false);
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Background", background.getSerializedName()); tag.putString("Framing", framing.name());
        tag.putString("Aspect", aspect.name()); tag.putString("Horizontal", horizontal.name());
        tag.putString("Vertical", vertical.name()); tag.putBoolean("Mirror", mirror); tag.putInt("BodyYaw", bodyYaw); return tag;
    }
    public static PhotoLayout load(CompoundTag tag) {
        if (tag.contains("BodyYaw") && !tag.contains("BodyYaw", net.minecraft.nbt.Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid photo body rotation type");
        return new PhotoLayout(FabricColor.fromSerializedName(tag.getString("Background")).orElseThrow(),
                Framing.valueOf(tag.getString("Framing")), Aspect.valueOf(tag.getString("Aspect")),
                Horizontal.valueOf(tag.getString("Horizontal")), Vertical.valueOf(tag.getString("Vertical")), tag.getBoolean("Mirror"), tag.getInt("BodyYaw"));
    }
}
