package com.rexhub.createapparel.fashion;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FashionConfig {
    public final ForgeConfigSpec.IntValue observationTicks, shoppingTicks, purchaseCooldown, failureCooldown, eventBudget, storageSlotLimit;
    public final ForgeConfigSpec.DoubleValue sameCitizenPoints, otherCitizenPoints, sameMannequinPoints, otherMannequinPoints,
            sameAdvertisementPoints, otherAdvertisementPoints, pointScale, impulseChance, shoppingChance,
            styleWeight, climateWeight, coldTemperature, hotTemperature;
    public final ForgeConfigSpec.IntValue photographyBaseCost, photographyExtra, maxPanels, maxChannels;

    public FashionConfig(ForgeConfigSpec.Builder builder) {
        builder.push("fashionRetail");
        observationTicks = builder.defineInRange("observationTicks", 200, 20, 72000);
        shoppingTicks = builder.defineInRange("shoppingTicks", 600, 20, 72000);
        purchaseCooldown = builder.defineInRange("purchaseCooldownTicks", 2400, 20, 144000);
        failureCooldown = builder.defineInRange("failureCooldownTicks", 600, 20, 144000);
        eventBudget = builder.defineInRange("maxEventsPerTick", 4, 1, 64);
        storageSlotLimit = builder.defineInRange("maxSlotsPerLinkedStorage", 4096, 27, 65536);
        sameCitizenPoints = builder.defineInRange("sameDistrictCitizenPoints", 4.0, 0, 1000);
        otherCitizenPoints = builder.defineInRange("otherDistrictCitizenPoints", 1.0, 0, 1000);
        sameMannequinPoints = builder.defineInRange("sameDistrictMannequinPoints", 8.0, 0, 1000);
        otherMannequinPoints = builder.defineInRange("otherDistrictMannequinPoints", 1.0, 0, 1000);
        sameAdvertisementPoints = builder.defineInRange("sameDistrictAdvertisementPoints", 8.0, 0, 1000);
        otherAdvertisementPoints = builder.defineInRange("otherDistrictAdvertisementPoints", 1.0, 0, 1000);
        pointScale = builder.defineInRange("pointProbabilityScale", 40.0, 1, 10000);
        impulseChance = builder.defineInRange("impulsePurchaseChance", 0.25, 0, 1);
        shoppingChance = builder.defineInRange("randomShoppingChance", 0.1, 0, 1);
        styleWeight = builder.defineInRange("styleMatchWeight", 0.25, 0, 2);
        climateWeight = builder.defineInRange("climateDemandWeight", 0.5, 0, 2);
        coldTemperature = builder.defineInRange("coldBiomeTemperature", 0.2, -2, 2);
        hotTemperature = builder.defineInRange("hotBiomeTemperature", 1.0, -2, 2);
        builder.pop();
        builder.push("advertisement");
        photographyBaseCost = builder.defineInRange("photographyBaseEmeralds", 20, 0, 1000);
        photographyExtra = builder.defineInRange("photographyRandomExtra", 10, 0, 1000);
        maxPanels = builder.defineInRange("maxPanelsPerScreen", 64, 1, 256);
        maxChannels = builder.defineInRange("maxChannels", 128, 1, 1024);
        builder.pop();
    }
}
