package com.rexhub.createapparel.compat.jei;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.MetalAccessoryItem;
import com.rexhub.createapparel.init.GarmentCreativePresets;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.villager.ModVillagers;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;

import java.util.List;

import static com.rexhub.createapparel.compat.jei.JeiClientTestLauncher.check;

final class TailorClientChecks {
    private TailorClientChecks() { }

    static void audit(IJeiRuntime runtime, RecipeType<AbstractCrushingRecipe> type) {
        var id = ResourceLocation.parse("createapparel:crushing/garments_to_old_cloth");
        var manager = runtime.getRecipeManager();
        var mc = Minecraft.getInstance();
        check(mc.getItemRenderer().getModel(new ItemStack(ModItems.OLD_CLOTH.get()), mc.level, mc.player, 0)
                != mc.getModelManager().getMissingModel(), "Old Cloth item model is baked");
        var recipes = manager.createRecipeLookup(type).get().filter(recipe -> recipe.getId().equals(id)).toList();
        check(recipes.size() == 1, "Exactly one native JEI crushing recipe");
        var factory = runtime.getJeiHelpers().getFocusFactory();
        for (var garment : ModItems.garmentCreativeTabItems()) {
            if (!(garment.get() instanceof GarmentItem)) continue;
            boolean recyclable = !(garment.get() instanceof MetalAccessoryItem)
                    && !List.of("glasses", "sunglasses", "watch").contains(garment.getId().getPath());
            for (var stack : List.of(new ItemStack(garment.get()), GarmentCreativePresets.create(garment))) {
                auditCrushingParticle(stack);
                var focus = factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
                check(manager.createRecipeLookup(type).limitFocus(List.of(focus)).get()
                        .anyMatch(recipe -> recipe.getId().equals(id)) == recyclable,
                        "Native crushing lookup respects accessory exclusions regardless of NBT: " + garment.getId());
            }
        }
        var output = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(ModItems.OLD_CLOTH.get()));
        check(manager.createRecipeLookup(type).limitFocus(List.of(output)).get().anyMatch(recipe -> recipe.getId().equals(id)),
                "Old Cloth output lookup");
        var layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), recipes.get(0), factory.getEmptyFocusGroup()).orElseThrow();
        check(layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream().flatMap(slot -> slot.getItemStacks())
                .anyMatch(stack -> stack.is(ModItems.OLD_CLOTH.get()) && stack.getCount() == 1 && !stack.hasTag()), "Native JEI result");
        for (String entity : List.of("villager", "zombie_villager")) {
            var texture = ResourceLocation.parse("createapparel:textures/entity/" + entity + "/profession/tailor.png");
            check(Minecraft.getInstance().getResourceManager().getResource(texture).isPresent(), "Profession overlay exists: " + entity);
        }
        com.mojang.logging.LogUtils.getLogger().info("TAILOR_JEI_CLIENT_PASSED");
    }

    private static void auditCrushingParticle(ItemStack stack) {
        var mc = Minecraft.getInstance();
        var model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        var resolved = model.getOverrides().resolve(model, stack, mc.level, null, 0);
        check(resolved != null, "Garment model override resolves: " + stack);
        var sprite = resolved.getParticleIcon(ModelData.EMPTY);
        check(sprite.contents().name().equals(ResourceLocation.parse("createapparel:item/old_cloth")),
                "Garment crushing uses the baked old cloth sprite: " + stack);
        var particle = new BreakingItemParticle.Provider().createParticle(
                new ItemParticleOption(ParticleTypes.ITEM, stack), mc.level, 0, 0, 0, 0, 0, 0);
        check(particle != null, "Native item particle is created: " + stack);
        var buffer = new BufferBuilder(256);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        particle.render(buffer, mc.gameRenderer.getMainCamera(), 0);
        var rendered = buffer.end();
        try {
            check(rendered.drawState().vertexCount() == 4, "Native crushing particle emits a quad");
            var vertices = rendered.vertexBuffer();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * DefaultVertexFormat.PARTICLE.getVertexSize() + 12;
                float u = vertices.getFloat(offset);
                float v = vertices.getFloat(offset + 4);
                check(u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1(),
                        "Native crushing particle samples the old cloth atlas region: " + stack);
            }
        } finally {
            rendered.release();
        }
    }

    static Screen tradeScreen() {
        var mc = Minecraft.getInstance();
        var villager = new Villager(EntityType.VILLAGER, mc.level);
        villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.TAILOR.get()));
        var menu = new net.minecraft.world.inventory.MerchantMenu(93, mc.player.getInventory(), villager);
        menu.setMerchantLevel(1);
        menu.setShowProgressBar(true);
        check(menu.getOffers().size() == 2, "Native merchant menu shows both novice offers");
        check(menu.getOffers().get(0).getBaseCostA().is(ModItems.OLD_CLOTH.get()), "Fixed old cloth buying offer");
        check(menu.getOffers().get(0).getBaseCostA().getCount() == 2
                && menu.getOffers().get(0).getResult().getCount() == 1, "Two old cloth for one emerald");
        return new net.minecraft.client.gui.screens.inventory.MerchantScreen(menu, mc.player.getInventory(), villager.getDisplayName());
    }

    static final class PreviewScreen extends Screen {
        private final Villager villager;
        private final ZombieVillager zombie;

        PreviewScreen() {
            super(Component.translatable("entity.minecraft.villager.createapparel.tailor"));
            var level = Minecraft.getInstance().level;
            villager = new Villager(EntityType.VILLAGER, level);
            villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.TAILOR.get()));
            zombie = new ZombieVillager(EntityType.ZOMBIE_VILLAGER, level);
            zombie.setVillagerData(villager.getVillagerData());
            String expected = Minecraft.getInstance().getLanguageManager().getSelected().equals("ja_jp") ? "被服職人" : "Tailor";
            check(villager.getDisplayName().getString().equals(expected), "Localized native profession title");
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF303238);
            graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
            int floor = height / 2 + 45;
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, width / 2 - 70, floor, 55, 0, 0, villager);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, width / 2 + 70, floor, 55, 0, 0, zombie);
            graphics.renderItem(new ItemStack(ModItems.OLD_CLOTH.get()), width / 2 - 8, floor + 15);
            graphics.drawCenteredString(font, ModItems.OLD_CLOTH.get().getDescription(), width / 2, floor + 36, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
