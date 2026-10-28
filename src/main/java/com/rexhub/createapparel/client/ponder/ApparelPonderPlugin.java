package com.rexhub.createapparel.client.ponder;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.guide.ProductionGuide;
import com.rexhub.createapparel.init.ModItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ApparelPonderPlugin implements PonderPlugin {
    public static final ResourceLocation PRODUCTION = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "production");
    public static final ResourceLocation RETAIL = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "retail");

    @Override
    public String getModId() { return CreateApparel.MOD_ID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM.getId())
                .addStoryBoard("production", ApparelRetailScenes::build, RETAIL);
        register(helper, List.of(ModItems.SPINNING_MACHINE.getId()), ProductionGuide.SPINNING, ProductionGuide.SPINNING_AUTOMATION);
        register(helper, List.of(ModItems.LOOM.getId()), ProductionGuide.WEAVING, ProductionGuide.WEAVING_AUTOMATION);
        register(helper, List.of(ModItems.SEWING_MACHINE.getId()),
                ProductionGuide.PATTERN, ProductionGuide.SEWING, ProductionGuide.SEWING_AUTOMATION);
        register(helper, List.of(ModItems.SILKWORM_REARING_TRAY.getId()),
                ProductionGuide.REARING, ProductionGuide.REARING_AUTOMATION);
    }

    private static void register(PonderSceneRegistrationHelper<ResourceLocation> helper,
                                 List<ResourceLocation> items, ProductionGuide... guides) {
        for (var guide : guides) {
            helper.forComponents(items).addStoryBoard("production", (builder, util) ->
                    ApparelProductionScenes.build(guide, builder, util), PRODUCTION);
        }
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(RETAIL).title("Apparel Shops").description("Cashiers, storage and linked displays")
                .item(com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM.get()).addToIndex().register();
        helper.addToTag(RETAIL).add(com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM.getId());
        helper.registerTag(PRODUCTION).title("Apparel Production")
                .description("Spinning, weaving, sewing and silkworm rearing")
                .item(ModItems.SEWING_MACHINE.get()).addToIndex().register();
        for (var item : List.of(ModItems.SPINNING_MACHINE, ModItems.LOOM, ModItems.SEWING_MACHINE, ModItems.SILKWORM_REARING_TRAY)) {
            helper.addToTag(PRODUCTION).add(item.getId());
        }
    }
}
