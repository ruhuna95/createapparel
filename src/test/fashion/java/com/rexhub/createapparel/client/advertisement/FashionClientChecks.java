package com.rexhub.createapparel.client.advertisement;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.advertisement.*;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.photo.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Path;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class FashionClientChecks {
    private static int stage, ticks, total;
    private static volatile AdvertisingPhotoData photo;
    private static volatile Throwable failure;
    private static volatile int menuCitizenId;
    private static long generation;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static int cameraStep;
    private static int cameraFrames;
    private static boolean heardTrade;
    private static net.minecraft.world.item.ItemStack cameraOutfit = net.minecraft.world.item.ItemStack.EMPTY;
    private static volatile boolean cameraOwnedPreserved;
    private static volatile boolean cameraStylesResent;
    private FashionClientChecks() { }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("apparel.fashionTest.client") || event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance(); if (mc.getOverlay() != null) return;
        try {
            if (failure != null) throw new AssertionError("Fashion server fixture failed", failure);
            if (++total > 2400) throw new AssertionError("Fashion client timed out at stage " + stage);
            if (stage == 0 && mc.screen != null) {
                stage = 1; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6); mc.options.guiScale().set(2);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720); mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("fashion-qa-" + System.nanoTime(),
                        new net.minecraft.world.level.LevelSettings("Fashion QA", net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                                false, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT), new WorldOptions(23, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()); return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (stage == 1) {
                stage = 2; mc.setScreen(null);
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld(); level.setDayTime(6000);
                        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                        var player = level.getServer().getPlayerList().getPlayers().get(0); player.connection.teleport(0.5, -58.5, -8, 0, 8);
                        player.getInventory().selected = 0; player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(PhotoRegistries.CAMERA.get()));
                        player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()));
                        player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(AdvertisementRegistries.POSTER_ITEM.get()));
                        player.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM.get()));
                        player.getInventory().setItem(4, new net.minecraft.world.item.ItemStack(AdvertisementRegistries.SIGNAGE_BROADCASTER_ITEM.get()));
                        level.setBlockAndUpdate(new BlockPos(-5, -60, -1), com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER.get().defaultBlockState());
                        level.setBlockAndUpdate(new BlockPos(-5, -60, 1), AdvertisementRegistries.SIGNAGE_BROADCASTER.get().defaultBlockState());
                        Citizen citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(level)); citizen.setPos(0, -60, 1); citizen.setNoAi(true);
                        level.addFreshEntity(citizen); photo = PhotographyService.snapshot(citizen); citizen.discard();
                        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) {
                            var pos = new BlockPos(1 - x, -60 + y, 0); level.setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
                            var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos); panel.initialize(player, new net.minecraft.nbt.CompoundTag());
                            if (x == 0 && y == 0) panel.content(AdvertisementSnapshot.photo(photo)); PanelGroupManager.merge(level, panel);
                        }
                        var posterPos = new BlockPos(5, -60, 0); var posterState = AdvertisementRegistries.POSTER.get().defaultBlockState();
                        level.setBlockAndUpdate(posterPos.south(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(posterPos.south().above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(posterPos, posterState); var posterItem = new net.minecraft.world.item.ItemStack(AdvertisementRegistries.POSTER_ITEM.get());
                        posterItem.getOrCreateTag().put("Advertisement", AdvertisementSnapshot.photo(photo).save());
                        player.getInventory().setItem(2, posterItem.copy());
                        var printedBillboard = new net.minecraft.world.item.ItemStack(AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get());
                        printedBillboard.getOrCreateTag().put("Advertisement", AdvertisementSnapshot.photo(photo).save());
                        player.getInventory().setItem(6, printedBillboard);
                        posterState.getBlock().setPlacedBy(level, posterPos, posterState, player, posterItem);
                        for (int y = 0; y < 2; y++) {
                            var pos = new BlockPos(-3, -60 + y, 0); level.setBlockAndUpdate(pos, AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState());
                            var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos); panel.initialize(player, new net.minecraft.nbt.CompoundTag());
                            if (y == 0) panel.channel("qa_channel"); PanelGroupManager.merge(level, panel);
                        }
                        AdvertisementNetworkSavedData.get(level).publish(player, "qa_channel", AdvertisementSnapshot.photo(photo));
                    } catch (Throwable throwable) { failure = throwable; }
                }); return;
            }
            if (photo == null || ++ticks < 40) return;
            if (!AdvertisementIconGenerator.generate()) return;
            if (stage == 2) {
                for (int slot = 0; slot < 5; slot++) {
                    var stack = mc.player.getInventory().getItem(slot);
                    if (stack.isEmpty() || mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0) == mc.getModelManager().getMissingModel()) {
                        throw new AssertionError("Fashion item model missing in slot " + slot);
                    }
                }
                if (!mc.player.getInventory().getItem(2).hasFoil() || !mc.player.getInventory().getItem(6).hasFoil()
                        || new net.minecraft.world.item.ItemStack(AdvertisementRegistries.POSTER_ITEM.get()).hasFoil())
                    throw new AssertionError("Printed poster and billboard items must glint while blank media remain plain");
                if (!capture(photo, 2, 1, "fashion-photo-landscape.png")) return;
                stage = 3; ticks = 0; return;
            }
            if (stage == 3) {
                if (!capture(photo, 1, 3, "fashion-photo-portrait.png")) return;
                stage = 4; ticks = 0; return;
            }
            if (stage == 4) {
                if (!(mc.level.getBlockEntity(new BlockPos(-3, -60, 0)) instanceof AdvertisementPanelBlockEntity signage)
                        || ClientAdvertisementChannels.snapshot(signage).photo() == null) return;
                var poster = (AdvertisementPanelBlockEntity) mc.level.getBlockEntity(new BlockPos(5, -60, 0));
                if (poster == null || poster.width() != 1 || poster.height() != 2 || poster.localSnapshot().photo() == null)
                    throw new AssertionError("The world poster must be a vertical printed 1 by 2 surface");
                var backing = mc.getBlockRenderer().getBlockModel(poster.getBlockState()).getParticleIcon(net.minecraftforge.client.model.data.ModelData.EMPTY);
                if (!backing.contents().name().equals(net.minecraft.resources.ResourceLocation.parse("minecraft:painting/back")))
                    throw new AssertionError("The painting backing must be stitched into the block atlas");
                screenshot("fashion-world.png"); stage = 5; ticks = 0;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        var citizen = Objects.requireNonNull(ModEntities.CITIZEN.get().create(player.serverLevel()));
                        citizen.setPos(0.5, -60, -6); citizen.setNoAi(true); player.serverLevel().addFreshEntity(citizen);
                        citizen.citizenState().loadout().setStackInSlot(com.rexhub.createapparel.garment.GarmentSlot.TOP.ordinal(),
                                new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.init.ModItems.SHIRT_LONGSLEEVES.get()));
                        var outfit = citizen.citizenState().loadout();
                        if (!outfit.setStyle(com.rexhub.createapparel.garment.GarmentSlot.TOP, "tuck", "tucked")
                                || !outfit.setStyle(com.rexhub.createapparel.garment.GarmentSlot.TOP, "sleeves", "rolled")
                                || !outfit.setStyle(com.rexhub.createapparel.garment.GarmentSlot.TOP, "open", "opened"))
                            throw new AssertionError("The camera model must start with non-default clothing styles");
                        menuCitizenId = citizen.getId();
                        player.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 64));
                        var trial = new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.init.ModItems.T_SHIRT.get());
                        trial.getOrCreateTag().putBoolean("CameraTrial", true); player.getInventory().setItem(10, trial);
                        PhotographyService.open(player, citizen, net.minecraft.world.InteractionHand.MAIN_HAND);
                    } catch (Throwable throwable) { failure = throwable; }
                }); return;
            }
            if (stage == 5) {
                if (!(mc.screen instanceof PhotographyScreen screen) || !checkCamera(mc, screen)) return;
                screenshot("fashion-photography-gui.png"); generation = com.rexhub.createapparel.client.garment.ClientGarmentAssets.INSTANCE.generation();
                mc.player.closeContainer(); reload = mc.reloadResourcePacks(); stage = 6; ticks = 0; return;
            }
            if (stage == 6) {
                if (!reload.isDone()) return; reload.join();
                var returned = mc.player.getInventory().getItem(10);
                if (!returned.is(com.rexhub.createapparel.init.ModItems.T_SHIRT.get()) || !returned.hasTag() || !returned.getTag().getBoolean("CameraTrial"))
                    throw new AssertionError("Closing did not synchronize the original owned clothing");
                if (com.rexhub.createapparel.client.garment.ClientGarmentAssets.INSTANCE.generation() <= generation) throw new AssertionError("Resource generation did not advance");
                if (!capture(photo, 4, 2, "fashion-photo-after-reload.png")) return;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        AdvertisementNetworkSavedData.get(player.serverLevel()).publish(player, "qa_channel", AdvertisementSnapshot.DEFAULT);
                    } catch (Throwable throwable) { failure = throwable; }
                }); stage = 7; ticks = 0; return;
            }
            if (stage == 7) {
                if (!(mc.level.getBlockEntity(new BlockPos(-3, -60, 0)) instanceof AdvertisementPanelBlockEntity signage)
                        || ClientAdvertisementChannels.snapshot(signage).photo() != null) return;
                var billboard = (AdvertisementPanelBlockEntity) mc.level.getBlockEntity(new BlockPos(1, -60, 0));
                if (billboard.localSnapshot().photo() == null) throw new AssertionError("Channel update changed a static advertisement");
                var playerPhoto = new AdvertisingPhotoData(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), mc.player.getUUID(), photo.appearance(), photo.age(),
                        photo.garments(), photo.pose(), photo.layout());
                if (!capture(playerPhoto, 2, 1, "fashion-player-photo.png")) return;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        var layout = photo.layout();
                        var fixed = photo.withLayout(new PhotoLayout(layout.background(), layout.framing(), layout.aspect(), layout.horizontal(), layout.vertical(), layout.mirror(), 75));
                        var item = new net.minecraft.world.item.ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); fixed.write(item);
                        player.getInventory().setItem(1, item); PhotographyMenu.openEdit(player, fixed, 1);
                    } catch (Throwable throwable) { failure = throwable; }
                }); stage = 70; ticks = 0; return;
            }
            if (stage == 70) {
                if (!(mc.screen instanceof PhotoEditScreen screen) || screen.getMenu().mode() != PhotographyMenu.Mode.EDIT) return;
                if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) com.rexhub.createapparel.compat.jei.FashionJeiChecks.checkHidden(screen);
                var buttons = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                        .map(net.minecraft.client.gui.components.Button.class::cast).toList();
                if (buttons.size() != 7 || !screen.getMenu().slots.isEmpty()
                        || buttons.stream().anyMatch(button -> button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents content
                        && content.getKey().equals("screen.createapparel.photo.body_yaw")))
                    throw new AssertionError("Photo editing must not offer pose or body direction settings");
                mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 7);
                mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 1);
                stage = 71; ticks = 0; return;
            }
            if (stage == 71) {
                if (!(mc.screen instanceof PhotoEditScreen screen) || screen.getMenu().preview().layout().framing() != PhotoLayout.Framing.UPPER_BODY || ticks < 44) return;
                if (screen.getMenu().preview().layout().bodyYaw() != 75) throw new AssertionError("Editing changed the captured body direction");
                screenshot("fashion-photo-edit-fixed-direction.png");
                mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 8);
                stage = 72; ticks = 0; return;
            }
            if (stage == 72) {
                if (mc.screen != null) return;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        var edited = AdvertisingPhotoData.read(player.getInventory().getItem(1));
                        if (edited == null || edited.layout().bodyYaw() != 75 || edited.layout().framing() != PhotoLayout.Framing.UPPER_BODY)
                            throw new AssertionError("Saved edits must retain captured rotation while allowing framing changes");
                        var citizen = (Citizen) player.serverLevel().getEntity(menuCitizenId);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                                new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.init.ModItems.SMART_PHONE.get()));
                        var interactionEvent = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,
                                net.minecraft.world.InteractionHand.MAIN_HAND, citizen);
                        ((com.rexhub.createapparel.item.SmartPhoneItem) com.rexhub.createapparel.init.ModItems.SMART_PHONE.get()).interact(interactionEvent);
                    } catch (Throwable throwable) { failure = throwable; }
                }); stage = 8; ticks = 0; return;
            }
            if (stage == 8) {
                if (!(mc.screen instanceof com.rexhub.createapparel.client.screen.CitizenScreen screen)) return;
                var button = lockButton(screen);
                int left = (mc.getWindow().getGuiScaledWidth() - 300) / 2;
                int top = (mc.getWindow().getGuiScaledHeight() - 228) / 2;
                if (button.getWidth() != 18 || button.getHeight() != 18 || button.getX() != left + 76 || button.getY() != top + 36
                        || button.getTooltip() == null) throw new AssertionError("Outfit lock button bounds or tooltip are incorrect");
                screenshot("fashion-outfit-unlocked.png");
                button.onPress(); stage = 9; ticks = 0; return;
            }
            if (stage == 9) {
                if (!(mc.screen instanceof com.rexhub.createapparel.client.screen.CitizenScreen screen)
                        || !screen.getMenu().citizen().citizenState().fashion().forceFashion()) return;
                screenshot("fashion-outfit-locked.png");
                mc.player.closeContainer();
                AdvertisementSceneClientChecks.start(mc); stage = 95; ticks = 0; return;
            }
            if (stage == 95 && AdvertisementSceneClientChecks.tick(mc)) {
                com.rexhub.createapparel.client.fashion.RetailClientChecks.start(mc); stage = 10; ticks = 0; return;
            }
            if (stage == 10 && com.rexhub.createapparel.client.fashion.RetailClientChecks.tick(mc)) {
                LogUtils.getLogger().info("FASHION_CLIENT_QA_SUCCESS"); stage = 99; mc.stop();
            }
        } catch (Throwable throwable) { stage = 99; LogUtils.getLogger().error("FASHION_CLIENT_QA_FAILED", throwable); mc.stop(); }
    }
    @SubscribeEvent public static void renderLockHover(net.minecraftforge.client.event.ScreenEvent.Render.Pre event) {
        if (!Boolean.getBoolean("apparel.fashionTest.client") || stage != 9
                || !(event.getScreen() instanceof com.rexhub.createapparel.client.screen.CitizenScreen screen)) return;
        var button = lockButton(screen);
        event.setCanceled(true);
        screen.renderWithTooltip(event.getGuiGraphics(), button.getX() + 9, button.getY() + 9, event.getPartialTick());
    }
    private static net.minecraft.client.gui.components.Button lockButton(com.rexhub.createapparel.client.screen.CitizenScreen screen) {
        return screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.getMessage().equals(net.minecraft.network.chat.Component.translatable("screen.createapparel.fashion.lock")))
                .findFirst().orElseThrow(() -> new AssertionError("Outfit lock icon button is missing"));
    }
    private static boolean capture(AdvertisingPhotoData data, int width, int height, String filename) throws java.io.IOException {
        var image = PhotoRenderCache.image(data, width, height); if (image == null) return false;
        if (!PhotoRenderCache.image(data, width, height).texture().equals(image.texture())) throw new AssertionError("Equivalent images do not share cache");
        int before = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        var texture = Minecraft.getInstance().getTextureManager().getTexture(image.texture());
        try (var pixels = new com.mojang.blaze3d.platform.NativeImage(image.width(), image.height(), true)) {
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture.getId()); pixels.downloadTexture(0, false);
            java.util.Set<Integer> colors = new java.util.HashSet<>();
            for (int x = 0; x < image.width(); x += 4) for (int y = 0; y < image.height(); y += 4) colors.add(pixels.getPixelRGBA(x, y));
            if (colors.size() < 5) throw new AssertionError("Photo render is blank: " + filename);
            pixels.flipY(); pixels.writeToFile(output(filename));
        } finally { com.mojang.blaze3d.platform.GlStateManager._bindTexture(before); }
        return true;
    }
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if (Boolean.getBoolean("apparel.fashionTest.client") && event.getSound() != null
                && event.getSound().getLocation().equals(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP.getLocation())) heardTrade = true;
    }
    private static boolean checkCamera(Minecraft mc, PhotographyScreen screen) throws java.io.IOException {
        var menu = screen.getMenu();
        int left = (mc.getWindow().getGuiScaledWidth() - PhotographyMenuLayout.WIDTH) / 2;
        int top = (mc.getWindow().getGuiScaledHeight() - PhotographyMenuLayout.HEIGHT) / 2;
        if (cameraStep == 0) {
            if (++cameraFrames < 8) return false;
            var initialStyles = menu.loadout().styleState(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles();
            if (!"tucked".equals(initialStyles.get("tuck")) || !"rolled".equals(initialStyles.get("sleeves"))
                    || !"opened".equals(initialStyles.get("open")))
                throw new AssertionError("Initial slot synchronization lost the model's worn clothing styles");
            screenshot("fashion-camera-initial-worn-styles.png");
            if (net.minecraftforge.fml.ModList.get().isLoaded("jei"))
                com.rexhub.createapparel.compat.jei.FashionJeiChecks.checkCamera(screen);
            var buttons = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).toList();
            if (buttons.stream().filter(button -> button.getX() == left + PhotographyMenuLayout.SETTINGS_X).count() != 8
                    || buttons.stream().noneMatch(com.rexhub.createapparel.client.screen.ColorButton.class::isInstance))
                throw new AssertionError("Camera must have eight settings including the shared color selector, without capture/cancel buttons");
            if (menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem()) throw new AssertionError("Unpaid photo result is visible");
            if (menu.preview().layout().background() != com.rexhub.createapparel.material.FabricColor.WHITE)
                throw new AssertionError("New camera photos must default to white");
            for (var slot : com.rexhub.createapparel.garment.GarmentSlot.values()) {
                var position = com.rexhub.createapparel.menu.ApparelMenu.apparelSlotPosition(slot);
                if (menu.getSlot(slot.ordinal()).x != position.x() + PhotographyMenuLayout.GARMENT_X_OFFSET || menu.getSlot(slot.ordinal()).y != position.y())
                    throw new AssertionError("Camera clothing grid differs from Wardrobe");
            }
            if (net.minecraft.network.chat.Component.translatable("screen.createapparel.photo.return_clothes").getString()
                    .equals("screen.createapparel.photo.return_clothes")) throw new AssertionError("Clothing return notice is not localized");
            cameraOutfit = menu.getSlot(com.rexhub.createapparel.garment.GarmentSlot.TOP.ordinal()).getItem().copy();
            transferClothing(screen, com.rexhub.createapparel.garment.GarmentSlot.TOP.ordinal(), PhotographyMenu.INVENTORY_START + 2, left, top);
            cameraStep = 10; return false;
        }
        int topSlot = com.rexhub.createapparel.garment.GarmentSlot.TOP.ordinal();
        if (cameraStep == 10) {
            if (!menu.getCarried().isEmpty() || menu.getSlot(topSlot).hasItem()
                    || !net.minecraft.world.item.ItemStack.matches(menu.getSlot(PhotographyMenu.INVENTORY_START + 2).getItem(), cameraOutfit)) return false;
            transferClothing(screen, PhotographyMenu.INVENTORY_START + 1, topSlot, left, top);
            cameraStep = 11; cameraFrames = 0; return false;
        }
        if (cameraStep == 11) {
            var trial = menu.getSlot(topSlot).getItem();
            if (!menu.getCarried().isEmpty() || !trial.hasTag() || !trial.getTag().getBoolean("CameraTrial") || ++cameraFrames < 4) return false;
            screenshot("fashion-camera-trial-clothes.png");
            transferClothing(screen, topSlot, PhotographyMenu.INVENTORY_START + 1, left, top);
            cameraStep = 12; return false;
        }
        if (cameraStep == 12) {
            if (!menu.getCarried().isEmpty() || menu.getSlot(topSlot).hasItem()
                    || !menu.getSlot(PhotographyMenu.INVENTORY_START + 1).hasItem()) return false;
            transferClothing(screen, PhotographyMenu.INVENTORY_START + 2, topSlot, left, top);
            cameraStep = 13; return false;
        }
        if (cameraStep == 13) {
            if (!menu.getCarried().isEmpty() || !net.minecraft.world.item.ItemStack.matches(menu.getSlot(topSlot).getItem(), cameraOutfit)) return false;
            mc.getSingleplayerServer().execute(() -> {
                var actual = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().getItem(10);
                if (!actual.is(com.rexhub.createapparel.init.ModItems.T_SHIRT.get()) || !actual.hasTag() || !actual.getTag().getBoolean("CameraTrial"))
                    failure = new AssertionError("Trial operations modified the real owned clothing");
                cameraOwnedPreserved = true;
            });
            cameraStep = 14; return false;
        }
        if (cameraStep == 14) {
            if (!cameraOwnedPreserved) return false;
            transferClothing(screen, topSlot, PhotographyMenu.INVENTORY_START + 2, left, top);
            transferClothing(screen, PhotographyMenu.INVENTORY_START + 1, topSlot, left, top);
            cameraStep = 15; return false;
        }
        if (cameraStep == 15) {
            if (!menu.getSlot(topSlot).getItem().is(com.rexhub.createapparel.init.ModItems.T_SHIRT.get())) return false;
            var style = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getX() == left + PhotographyMenuLayout.STYLE_X).findFirst();
            if (style.isEmpty()) return false;
            if (!style.get().active || style.get().getMessage().getString().contains("style.createapparel."))
                throw new AssertionError("Camera style selector is disabled or not localized");
            style.get().onPress();
            cameraStep = 16; return false;
        }
        if (cameraStep == 16) {
            if (!"tucked".equals(menu.loadout().styleState(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles().get("tuck"))) return false;
            screenshot("fashion-camera-style-selector.png");
            screen.mouseScrolled(left + 40, top + 70, -1);
            screen.mouseScrolled(left + 40, top + 70, -1);
            screen.mouseScrolled(left + 40, top + 70, 1);
            cameraStep = 17; return false;
        }
        if (cameraStep == 17) {
            if (menu.setting(7) == topSlot) return false;
            screen.mouseScrolled(left + 40, top + 70, 1);
            cameraStep = 18; return false;
        }
        if (cameraStep == 18) {
            if (menu.setting(7) != topSlot) return false;
            screen.mouseScrolled(left + PhotographyMenuLayout.STYLE_X + 10, top + PhotographyMenuLayout.STYLE_Y + 9, 1);
            cameraStep = 19; return false;
        }
        if (cameraStep == 19) {
            if (!"untucked".equals(menu.loadout().styleState(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles().get("tuck"))) return false;
            screen.mouseScrolled(left + PhotographyMenuLayout.STYLE_X + 10, top + PhotographyMenuLayout.STYLE_Y + 9, -1);
            cameraStep = 20; return false;
        }
        if (cameraStep == 20) {
            if (!"tucked".equals(menu.loadout().styleState(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles().get("tuck"))) return false;
            mc.getSingleplayerServer().execute(() -> {
                mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).containerMenu.broadcastFullState();
                cameraStylesResent = true;
            });
            cameraStep = 21; cameraFrames = 0; return false;
        }
        if (cameraStep == 21) {
            if (!cameraStylesResent || ++cameraFrames < 5) return false;
            if (!"tucked".equals(menu.loadout().styleState(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles().get("tuck")))
                throw new AssertionError("Full slot synchronization reset the trial garment style");
            screenshot("fashion-camera-style-selector.png");
            screen.children().stream().filter(com.rexhub.createapparel.client.screen.ColorButton.class::isInstance)
                    .map(com.rexhub.createapparel.client.screen.ColorButton.class::cast).findFirst().orElseThrow().onPress();
            cameraStep = 1; return false;
        }
        if (cameraStep == 1) {
            screenshot("fashion-camera-color-selector.png");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            int x = left + PhotographyMenuLayout.SETTINGS_X + 20;
            int y = top + PhotographyMenuLayout.SETTINGS_Y + 7 * PhotographyMenuLayout.BUTTON_STEP + PhotographyMenuLayout.BUTTON_HEIGHT / 2;
            screen.mouseScrolled(x, y, 1);
            screen.mouseScrolled(x, y, -1);
            screen.mouseScrolled(x, y, -1);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, PhotographyMenu.INVENTORY_START, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
            cameraStep = 2; return false;
        }
        if (cameraStep == 2) {
            if (menu.preview().layout().bodyYaw() != 15 || !menu.getSlot(PhotographyMenu.RESULT_SLOT).hasItem()) return false;
            if (++cameraFrames < 4) return false;
            screenshot("fashion-camera-paid-preview.png");
            if (!capture(menu.preview(), 2, 1, "fashion-photo-rotated.png")) return false;
            heardTrade = false;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, PhotographyMenu.RESULT_SLOT, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            cameraStep = 3; return false;
        }
        if (cameraStep == 3) {
            var photo = AdvertisingPhotoData.read(menu.getCarried());
            if (photo == null || !heardTrade) return false;
            if (photo.layout().bodyYaw() != 15 || menu.getSlot(PhotographyMenu.PAYMENT_SLOT).getItem().getCount() != 64 - menu.cost())
                throw new AssertionError("Camera trade lost rotation or charged the wrong fee");
            if (!"tucked".equals(photo.loadout(com.rexhub.createapparel.client.garment.ClientGarmentAssets::getDefinition)
                    .styleState(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles().get("tuck")))
                throw new AssertionError("The issued photo lost the selected garment style");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, PhotographyMenu.INVENTORY_START, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            cameraStep = 4; return false;
        }
        if (cameraStep == 4) {
            if (!menu.getCarried().isEmpty()) return false;
            mc.options.guiScale().set(3);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 720); mc.resizeDisplay();
            cameraStep = 5; cameraFrames = 0; return false;
        }
        if (cameraStep == 5) {
            if (++cameraFrames < 4) return false;
            if (mc.getWindow().getGuiScaledWidth() != 320 || mc.getWindow().getGuiScaledHeight() != 240 || left < 0 || top < 0)
                throw new AssertionError("Camera does not fit the minimum vanilla GUI dimensions");
            screenshot("fashion-camera-compact.png");
            mc.options.guiScale().set(2);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720); mc.resizeDisplay();
            cameraStep = 6; cameraFrames = 0; return false;
        }
        return ++cameraFrames >= 4;
    }
    private static void transferClothing(PhotographyScreen screen, int source, int target, int left, int top) {
        for (int index : new int[]{source, target}) {
            var slot = screen.getMenu().getSlot(index);
            int x = left + slot.x + 8, y = top + slot.y + 8;
            screen.mouseClicked(x, y, 0);
            screen.mouseDragged(x, y, 0, 0, 0);
            screen.mouseReleased(x, y, 0);
        }
    }
    private static Path output(String name) { return Path.of(System.getProperty("apparel.fashionTest.projectDir"), "build", name); }
    private static void screenshot(String name) throws java.io.IOException {
        try (var pixels = net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { pixels.writeToFile(output(name)); }
    }
}
