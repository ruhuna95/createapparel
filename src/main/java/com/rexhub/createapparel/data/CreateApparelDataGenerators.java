package com.rexhub.createapparel.data;

import com.rexhub.createapparel.data.client.MannequinTextureProvider;
import com.rexhub.createapparel.data.client.ModItemModelProvider;
import com.rexhub.createapparel.data.client.ModJapaneseLanguageProvider;
import com.rexhub.createapparel.data.client.ModLanguageProvider;
import com.rexhub.createapparel.data.server.ModBlockTagProvider;
import com.rexhub.createapparel.data.server.ModGlobalLootModifierProvider;
import com.rexhub.createapparel.data.server.ModItemTagProvider;
import com.rexhub.createapparel.data.server.ModLootTableProvider;
import com.rexhub.createapparel.data.server.ModRecipeProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;

public final class CreateApparelDataGenerators {
    private CreateApparelDataGenerators() {
    }

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(event.includeClient(),
                new com.rexhub.createapparel.data.client.MirrorModelProvider(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(),
                new com.rexhub.createapparel.data.client.SilkwormRearingTrayModelProvider(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(),
                new com.rexhub.createapparel.data.client.FurnitureModelProvider(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(),
                new MannequinTextureProvider(output));
        generator.addProvider(event.includeClient(),
                new com.rexhub.createapparel.data.client.CitizenTextureProvider(output));

        ModBlockTagProvider blockTags = generator.addProvider(
                event.includeServer(),
                new ModBlockTagProvider(output, event.getLookupProvider(), event.getExistingFileHelper())
        );
        generator.addProvider(
                event.includeServer(),
                new ModItemTagProvider(
                        output,
                        event.getLookupProvider(),
                        blockTags.contentsGetter(),
                        event.getExistingFileHelper()
                )
        );
        generator.addProvider(
                event.includeClient(),
                new ModItemModelProvider(output, event.getExistingFileHelper())
        );
        generator.addProvider(event.includeServer(), ModLootTableProvider.create(output));
        generator.addProvider(
                event.includeServer(),
                new ModGlobalLootModifierProvider(output)
        );
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output));
        generator.addProvider(event.includeClient(), new ModJapaneseLanguageProvider(output));
    }
}
