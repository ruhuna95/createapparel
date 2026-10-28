package com.rexhub.createapparel.fashion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import java.util.Set;

public record FashionDesignModifier(Set<FashionTag> add, Set<FashionTag> remove) {
    public FashionDesignModifier {
        add = Set.copyOf(add);
        remove = Set.copyOf(remove);
        if (java.util.stream.Stream.concat(add.stream(), remove.stream())
                .anyMatch(tag -> tag.category() == FashionTag.Category.CLIMATE))
            throw new IllegalArgumentException("Design modifiers cannot change climate tags");
    }
    public void apply(Set<FashionTag> tags) { tags.removeAll(remove); tags.addAll(add); }
    public static FashionDesignModifier parse(JsonObject json) {
        return new FashionDesignModifier(FashionTag.parse(GsonHelper.getAsJsonArray(json, "add", new JsonArray())),
                FashionTag.parse(GsonHelper.getAsJsonArray(json, "remove", new JsonArray())));
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeEnumSet(add.isEmpty() ? java.util.EnumSet.noneOf(FashionTag.class) : java.util.EnumSet.copyOf(add), FashionTag.class);
        buffer.writeEnumSet(remove.isEmpty() ? java.util.EnumSet.noneOf(FashionTag.class) : java.util.EnumSet.copyOf(remove), FashionTag.class);
    }
    public static FashionDesignModifier read(FriendlyByteBuf buffer) {
        return new FashionDesignModifier(buffer.readEnumSet(FashionTag.class), buffer.readEnumSet(FashionTag.class));
    }
}
