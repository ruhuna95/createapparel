package com.rexhub.createapparel.material;

import com.rexhub.createapparel.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class FabricAttributeMap {
    private static volatile Map<Item, FabricAttribute> attributes;

    private FabricAttributeMap() {
    }

    public static Optional<FabricAttribute> get(ItemStack stack) {
        return get(stack.getItem());
    }

    private static Optional<FabricAttribute> get(Item item) {
        return Optional.ofNullable(attributes().get(item));
    }

    public static Optional<FabricColor> color(ItemStack stack) {
        return get(stack).flatMap(FabricAttribute::color);
    }

    public static boolean isDyed(ItemStack stack) {
        return get(stack).map(FabricAttribute::isDyed).orElse(false);
    }

    private static Map<Item, FabricAttribute> attributes() {
        Map<Item, FabricAttribute> current = attributes;
        if (current != null) {
            return current;
        }
        synchronized (FabricAttributeMap.class) {
            if (attributes == null) {
                IdentityHashMap<Item, FabricAttribute> resolved = new IdentityHashMap<>();
                for (Map.Entry<RegistryObject<Item>, FabricAttribute> entry : ModItems.fabricItems().entrySet()) {
                    FabricAttribute previous = resolved.put(entry.getKey().get(), entry.getValue());
                    if (previous != null) {
                        throw new IllegalStateException("A fabric item has more than one static attribute mapping");
                    }
                }
                attributes = Collections.unmodifiableMap(resolved);
            }
            return attributes;
        }
    }
}
