package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.advertisement.*;
import com.rexhub.createapparel.photo.PhotoLayout;
import com.rexhub.createapparel.photo.PhotoRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;

final class AdvertisementSceneClientChecks {
    private static final BlockPos BILLBOARD = new BlockPos(3, -60, 4), SIGNAGE = new BlockPos(-1, -60, 4), BROADCASTER = new BlockPos(-4, -60, 1);
    private static int stage, ticks;
    private static volatile Throwable failure;
    private AdvertisementSceneClientChecks() { }
    static void start(Minecraft mc) {
        server(mc, player -> {
            var level = player.serverLevel(); player.connection.teleport(.5, -58.5, 2, 15, 8);
            for (var origin : List.of(BILLBOARD, SIGNAGE)) {
                var block = origin.equals(BILLBOARD) ? AdvertisementRegistries.BILLBOARD_PANEL.get() : AdvertisementRegistries.SIGNAGE_PANEL.get();
                int width = origin.equals(BILLBOARD) ? 3 : 2, height = origin.equals(BILLBOARD) ? 2 : 3;
                for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                    var pos = origin.west(x).above(y); level.setBlockAndUpdate(pos, block.defaultBlockState());
                    var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos); panel.initialize(player, new net.minecraft.nbt.CompoundTag());
                    PanelGroupManager.merge(level, panel);
                }
            }
            level.setBlockAndUpdate(BROADCASTER, AdvertisementRegistries.SIGNAGE_BROADCASTER.get().defaultBlockState());
            AdvertisementControlMenu.open(player, BILLBOARD, InteractionHand.MAIN_HAND);
        });
    }
    static boolean tick(Minecraft mc) throws java.io.IOException {
        if (failure != null) throw new AssertionError("Advertisement scene fixture failed", failure);
        if (++ticks > 700) throw new AssertionError("Advertisement scene client timed out at " + stage);
        if (stage == 0 && mc.screen instanceof AdvertisementControlScreen screen) {
            checkHidden(screen); var menu = screen.getMenu();
            if (menu.kind() != AdvertisementControlMenu.Kind.BILLBOARD || !menu.slots.isEmpty() || buttons(screen).size() != 6
                    || menu.panelWidth() != 3 || menu.panelHeight() != 2) throw new AssertionError("Billboard GUI must contain only a rectangle preview and six layout controls");
            AdvertisementNetwork.control(menu.containerId, "", 0, 1); stage = 1; return false;
        }
        if (stage == 1 && mc.screen instanceof AdvertisementControlScreen screen && screen.getMenu().preview(0).layout().framing() == PhotoLayout.Framing.UPPER_BODY) {
            screenshot(mc, "fashion-billboard-settings.png"); mc.player.closeContainer();
            server(mc, player -> {
                var panel = (AdvertisementPanelBlockEntity) player.level().getBlockEntity(BILLBOARD);
                if (panel.localSnapshot().displayPhoto().layout().framing() != PhotoLayout.Framing.UPPER_BODY) throw new AssertionError("Closing must save billboard layout");
                for (int index = 0; index < 9; index++) {
                    var stack = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get());
                    AdvertisementScenes.defaultSnapshot(index % 3).displayPhoto().copyIdentity().write(stack); player.getInventory().setItem(9 + index, stack);
                }
                AdvertisementControlMenu.open(player, BROADCASTER, InteractionHand.MAIN_HAND);
            }); stage = 2; return false;
        }
        if (stage == 2 && mc.screen instanceof AdvertisementControlScreen screen && screen.getMenu().kind() == AdvertisementControlMenu.Kind.BROADCASTER) {
            checkHidden(screen); if (screen.getMenu().slots.size() != 45 || !buttons(screen).isEmpty()) throw new AssertionError("Broadcaster needs nine photo slots and inventory, without action buttons");
            var input = channel(screen); screen.setFocused(input); input.setFocused(true);
            if (!screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0) || mc.screen != screen) throw new AssertionError("Typing the inventory key into a channel must not close the menu");
            screen.charTyped('e', 0); input.setValue("qa_nine"); input.setFocused(false);
            for (int index = 0; index < 9; index++) mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 9 + index, 0, ClickType.QUICK_MOVE, mc.player);
            stage = 3; return false;
        }
        if (stage == 3 && mc.screen instanceof AdvertisementControlScreen screen) {
            for (int index = 0; index < 9; index++) if (!screen.getMenu().getSlot(index).hasItem()) return false;
            screenshot(mc, "fashion-broadcaster-nine-scenes.png"); mc.player.closeContainer();
            server(mc, player -> {
                if (AdvertisementNetworkSavedData.get(player.serverLevel()).channel("qa_nine").scenes().size() != 9) throw new AssertionError("Nine scenes were not published on close");
                AdvertisementControlMenu.open(player, SIGNAGE, InteractionHand.MAIN_HAND);
            }); stage = 4; return false;
        }
        if (stage == 4 && mc.screen instanceof AdvertisementControlScreen screen && screen.getMenu().kind() == AdvertisementControlMenu.Kind.SIGNAGE) {
            checkHidden(screen); if (!screen.getMenu().slots.isEmpty() || buttons(screen).size() != 7 || screen.getMenu().scenes().size() != 3) throw new AssertionError("Unconfigured signage must preview three default scenes");
            channel(screen).setValue("qa_nine"); stage = 5; return false;
        }
        if (stage == 5 && mc.screen instanceof AdvertisementControlScreen screen && screen.getMenu().scenes().size() == 9) {
            var select = buttons(screen).stream().filter(button -> button.getY() == screen.getGuiTop() + 8).findFirst().orElseThrow();
            for (int index = 0; index < 8; index++) select.onPress();
            AdvertisementNetwork.control(screen.getMenu().containerId, "qa_nine", 8, 1); stage = 6; return false;
        }
        if (stage == 6 && mc.screen instanceof AdvertisementControlScreen screen && screen.getMenu().preview(8).layout().framing() == PhotoLayout.Framing.UPPER_BODY) {
            screenshot(mc, "fashion-signage-scene-settings.png"); mc.player.closeContainer();
            server(mc, player -> {
                var panel = (AdvertisementPanelBlockEntity) player.level().getBlockEntity(SIGNAGE);
                if (!panel.channel().equals("qa_nine") || panel.sceneLayouts().size() != 1 || !panel.sceneLayouts().containsKey(8)) throw new AssertionError("Signage must save only the edited scene override");
                if (panel.getBlockState().getLightEmission(player.level(), SIGNAGE) != 15) throw new AssertionError("Signage must emit level fifteen light");
                if (AdvertisementNetworkSavedData.get(player.serverLevel()).channel("qa_nine").scenes().get(8).displayPhoto().layout().framing() != PhotoLayout.Framing.FULL_BODY)
                    throw new AssertionError("Panel layout changes must not rewrite published photo data");
            }); stage = 7; return false;
        }
        if (stage == 7 && mc.screen == null && mc.level.getBlockEntity(SIGNAGE) instanceof AdvertisementPanelBlockEntity panel
                && ClientAdvertisementChannels.scenes(panel).size() == 9 && mc.level.getGameTime() % AdvertisementScenes.SCENE_TICKS >= 185) {
            screenshot(mc, "fashion-signage-crossfade.png"); stage = 8; return true;
        }
        return stage == 8;
    }
    private static List<Button> buttons(AdvertisementControlScreen screen) { return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList(); }
    private static EditBox channel(AdvertisementControlScreen screen) { return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow(); }
    private static void checkHidden(AdvertisementControlScreen screen) { if (net.neoforged.fml.ModList.get().isLoaded("jei")) com.rexhub.createapparel.compat.jei.FashionJeiChecks.checkHidden(screen); }
    private static void screenshot(Minecraft mc, String name) throws java.io.IOException {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of(System.getProperty("apparel.fashionTest.projectDir"), "build", name)); }
    }
    private static void server(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        mc.getSingleplayerServer().execute(() -> { try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)); } catch (Throwable throwable) { failure = throwable; } });
    }
}
