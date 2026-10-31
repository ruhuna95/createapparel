package com.rexhub.createapparel.garment;

import com.rexhub.createapparel.init.ModTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;

public record MetalAccessoryData(MetalColor metalColor, Gem gem) implements GarmentAppearance {
    public static final String TAG_NAME = "MetalAccessoryData";
    public static final MetalAccessoryData DEFAULT = new MetalAccessoryData(MetalColor.GOLD, Gem.DIAMOND);

    public MetalAccessoryData {
        Objects.requireNonNull(metalColor);
        Objects.requireNonNull(gem);
    }

    public static MetalAccessoryData fromStack(ItemStack stack) {
        CompoundTag root = com.rexhub.createapparel.item.ItemData.read(stack);
        return root == null || !root.contains(TAG_NAME, CompoundTag.TAG_COMPOUND)
                ? DEFAULT : fromNBT(root.getCompound(TAG_NAME));
    }

    public static MetalAccessoryData fromNBT(CompoundTag tag) {
        return new MetalAccessoryData(
                parse(MetalColor.values(), tag.getString("metal_color")),
                parse(Gem.values(), tag.getString("gem"))
        );
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("metal_color", metalColor.getSerializedName());
        tag.putString("gem", gem.getSerializedName());
        return tag;
    }

    @Override
    public void write(ItemStack stack) {
        CompoundTag root = com.rexhub.createapparel.item.ItemData.readOrEmpty(stack);
        root.remove(GarmentData.TAG_NAME);
        root.put(TAG_NAME, serializeNBT());
        com.rexhub.createapparel.item.ItemData.write(stack, root);
    }

    @Override
    public String design() {
        return "plain";
    }

    @Override
    public int primaryTintRgb() {
        return metalColor.tintRgb();
    }

    @Override
    public OptionalInt secondaryTintRgb() {
        return OptionalInt.of(gem.tintRgb());
    }

    @Override
    public OptionalInt accentTintRgb() {
        return OptionalInt.empty();
    }

    private static <T extends Enum<T> & StringRepresentable> T parse(T[] values, String name) {
        return Arrays.stream(values).filter(value -> value.getSerializedName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown accessory appearance: " + name));
    }

    public enum MetalColor implements StringRepresentable {
        GOLD(0xF7D568), SILVER(0xE0E6EC);

        private final int tintRgb;

        MetalColor(int tintRgb) {
            this.tintRgb = tintRgb;
        }

        public int tintRgb() {
            return tintRgb;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Gem implements StringRepresentable {
        DIAMOND(0xA8EFEB), EMERALD(0x50D980), AMETHYST(0xB58AEB),
        RUBY(0xE85268), SAPPHIRE(0x527EE8), PEARL(0xFFF2D8);

        private final int tintRgb;
        Gem(int tintRgb) {
            this.tintRgb = tintRgb;
        }

        public int tintRgb() {
            return tintRgb;
        }

        public TagKey<Item> tag() {
            return switch (this) {
                case DIAMOND -> ModTags.Items.GEMS_DIAMOND;
                case EMERALD -> ModTags.Items.GEMS_EMERALD;
                case AMETHYST -> ModTags.Items.GEMS_AMETHYST;
                case RUBY -> ModTags.Items.GEMS_RUBY;
                case SAPPHIRE -> ModTags.Items.GEMS_SAPPHIRE;
                case PEARL -> ModTags.Items.GEMS_PEARL;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
