package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.config.ModConfigs;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

public final class FashionEvaluator {
    public record Demand(java.util.Map<FashionTag, Integer> styles, int total, @javax.annotation.Nullable FashionTag climate, int climateCount) {
        public Demand { styles = java.util.Map.copyOf(styles); }
    }
    private FashionEvaluator() { }
    public static boolean eligible(CitizenAge age, ChestProfile chest, Set<FashionTag> tags) {
        return !(age == CitizenAge.ADULT && tags.contains(FashionTag.KIDS))
                && !(chest == ChestProfile.BUST && tags.contains(FashionTag.MASCULINE))
                && !(chest == ChestProfile.FLAT && tags.contains(FashionTag.FEMININE));
    }
    public static boolean eligible(Citizen citizen, ItemStack stack, GarmentSlot slot) {
        if (citizen.citizenState().fashion().forceFashion() || stack.isEmpty()) return false;
        var loadout = citizen.citizenState().loadout();
        try {
            var definition = loadout.definition(stack).orElse(null);
            if (definition == null || !definition.allowedSlots().contains(slot)
                    || !eligible(citizen.citizenState().age(), citizen.citizenState().appearance().chestProfile(), FashionTagResolver.resolve(stack))) return false;
            if (ItemStack.isSameItemSameComponents(loadout.getStack(slot), stack)) return false;
            var candidate = loadout.copy(); candidate.setStackInSlot(slot.ordinal(), stack.copyWithCount(1));
            return com.rexhub.createapparel.citizen.CitizenCoverage.isCovered(candidate);
        } catch (IllegalArgumentException exception) { return false; }
    }
    public static Demand demand(Citizen citizen) {
        var config = ModConfigs.COMMON.fashion;
        int total = 0, climateCount = 0;
        java.util.Map<FashionTag, Integer> styles = new java.util.EnumMap<>(FashionTag.class);
        float temperature = citizen.level().getBiome(citizen.blockPosition()).value().getBaseTemperature();
        FashionTag climate = temperature <= config.coldTemperature.get() ? FashionTag.THICK
                : temperature >= config.hotTemperature.get() ? FashionTag.THIN : null;
        for (var wornSlot : GarmentSlot.values()) {
            var worn = citizen.citizenState().loadout().getStack(wornSlot);
            if (worn.isEmpty()) continue;
            Set<FashionTag> wornTags;
            try { wornTags = FashionTagResolver.resolve(worn); } catch (IllegalArgumentException ignored) { continue; }
            if (climate != null && wornTags.contains(climate)) climateCount++;
            for (var tag : wornTags) if (tag.category() == FashionTag.Category.STYLE) {
                total++; styles.merge(tag, 1, Integer::sum);
            }
        }
        return new Demand(styles, total, climate, climateCount);
    }
    public static double attractiveness(ItemStack stack, Demand demand) {
        var config = ModConfigs.COMMON.fashion; var tags = FashionTagResolver.resolve(stack);
        int matching = tags.stream().mapToInt(tag -> demand.styles().getOrDefault(tag, 0)).sum();
        double climateBonus = demand.climate() != null && tags.contains(demand.climate()) ? config.climateWeight.get() / (1 + demand.climateCount()) : 0;
        return 1 + config.styleWeight.get() * (demand.total() == 0 ? 0 : (double) matching / demand.total()) + climateBonus;
    }
    public static double probability(double points, double scale) { return -Math.expm1(-Math.max(0, points) / scale); }
}
