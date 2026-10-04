package com.rexhub.createapparel.material;

import com.rexhub.createapparel.item.ApparelDyeItem;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;

public enum FabricColor implements StringRepresentable {
    WHITE(0xF1EEE8),
    ORANGE(0xC88743),
    MAGENTA(0xB06EA5),
    LIGHT_BLUE(0x7FA9D1),
    YELLOW(0xD6B14F),
    LIME(0x8FB955),
    PINK(0xD7A0A7),
    GRAY(0x7C828A),
    LIGHT_GRAY(0xC6CBD2),
    CYAN(0x4E98A0),
    PURPLE(0x7D64A8),
    BLUE(0x5872AB),
    BROWN(0x7A5C43),
    GREEN(0x5F8B59),
    RED(0xA54B4B),
    BLACK(0x2E3138),
    CREAM("cream", 0xEDE0C0),
    BEIGE("beige", 0xCDBB9A),
    GREIGE("greige", 0xAAA294),
    TAN("tan", 0xB58D67),
    NAVY("navy", 0x25354D),
    CERULEAN("cerulean", 0x007BA7),
    SKY("sky", 0x91B8CE),
    OLIVE("olive", 0x6D7045),
    KHAKI("khaki", 0x827C56),
    FOREST("forest", 0x334C3A),
    MINT("mint", 0xA3C6B0),
    BURGUNDY("burgundy", 0x672F3B),
    CORAL("coral", 0xD78374),
    ROSE("rose", 0xB96070),
    MUSTARD("mustard", 0xB49642),
    LAVENDER("lavender", 0xACA0BD),
    PLUM("plum", 0x68485F);

    private final boolean vanilla;
    private final String id;
    private final int tintRgb;

    static {
        var colors = new HashSet<String>();
        for (FabricColor color : values()) {
            if (!colors.add(color.id)) {
                throw new IllegalStateException("Duplicate fabric color: " + color.id);
            }
        }
    }

    FabricColor(int tintRgb) {
        this.vanilla = true;
        this.id = name().toLowerCase(Locale.ROOT);
        this.tintRgb = tintRgb;
    }

    FabricColor(String id, int tintRgb) {
        this.vanilla = false;
        this.id = id;
        this.tintRgb = tintRgb;
    }

    public Optional<DyeColor> vanillaDyeColor() {
        return vanilla ? Optional.of(DyeColor.byName(id, DyeColor.WHITE)) : Optional.empty();
    }

    public boolean isVanilla() {
        return vanilla;
    }

    public String translationKey() {
        return "color." + (vanilla ? "minecraft." : "createapparel.") + id;
    }

    public static Optional<FabricColor> fromDye(ItemStack stack) {
        if (stack.getItem() instanceof ApparelDyeItem dye) {
            return Optional.of(dye.color());
        }
        if (stack.getItem() instanceof DyeItem dye) {
            return fromSerializedName(dye.getDyeColor().getName());
        }
        return Optional.empty();
    }

    public int tintRgb() {
        return tintRgb;
    }

    public static Optional<FabricColor> fromSerializedName(String name) {
        for (FabricColor color : values()) {
            if (color.getSerializedName().equals(name)) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
