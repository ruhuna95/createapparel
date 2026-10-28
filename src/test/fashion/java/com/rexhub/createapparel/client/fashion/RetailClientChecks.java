package com.rexhub.createapparel.client.fashion;

import com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity;
import com.rexhub.createapparel.advertisement.AdvertisementSnapshot;
import com.rexhub.createapparel.fashion.CashRegisterBlockEntity;
import com.rexhub.createapparel.fashion.FashionRegistries;
import com.rexhub.createapparel.garment.*;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public final class RetailClientChecks {
    private static final BlockPos REGISTER = new BlockPos(-5, -60, -1), STORAGE = new BlockPos(-7, -60, -1);
    private static final BlockPos ADVERTISEMENT = new BlockPos(1, -60, 0);
    private static int stage, ticks;
    private static volatile int mannequinId, cashierId;
    private static volatile Throwable failure;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    private RetailClientChecks() { }

    public static void start(Minecraft mc) {
        server(mc, () -> {
            var level = mc.getSingleplayerServer().overworld();
            var player = level.getServer().getPlayerList().getPlayers().get(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
            player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
            level.setBlockAndUpdate(STORAGE, Blocks.CHEST.defaultBlockState());
            var register = (CashRegisterBlockEntity) level.getBlockEntity(REGISTER);
            if (!register.linkStorage(STORAGE)) throw new AssertionError("Retail client storage did not link");
            var seatPos = REGISTER.north();
            level.setBlockAndUpdate(seatPos, AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState());
            var seat = new SeatEntity(level, seatPos); seat.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(seat);
            var cashier = Objects.requireNonNull(EntityType.COW.create(level));
            cashier.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(cashier); cashier.startRiding(seat, true); cashierId = cashier.getId();
            var mannequin = Objects.requireNonNull(ModEntities.FULL_MANNEQUIN_WHITE_CLASSIC.get().create(level));
            mannequin.setPos(Vec3.atBottomCenterOf(REGISTER.south(2)));
            mannequin.mannequinState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), shirt());
            mannequin.mannequinState().setLinkedRegister(register.reference()); level.addFreshEntity(mannequin); mannequinId = mannequin.getId();
            var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(ADVERTISEMENT);
            var template = com.rexhub.createapparel.advertisement.AdvertisementScenes.DEFAULTS.get(0).displayPhoto();
            panel.content(AdvertisementSnapshot.photo(new com.rexhub.createapparel.photo.AdvertisingPhotoData(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                    template.appearance(), template.age(), com.rexhub.createapparel.photo.AdvertisingPhotoData.garments(mannequin.mannequinState().loadout()),
                    com.rexhub.createapparel.entity.PosePreset.STANDING, com.rexhub.createapparel.photo.PhotoLayout.DEFAULT)));
            panel.link(register.reference());
            player.connection.teleport(-0.5, -60, -0.5, 90, 16);
            player.inventoryMenu.broadcastChanges();
        });
    }

    public static boolean tick(Minecraft mc) throws java.io.IOException {
        if (failure != null) throw new AssertionError("Retail server fixture failed", failure);
        if (++ticks < 40) return false;
        var register = (CashRegisterBlockEntity) mc.level.getBlockEntity(REGISTER);
        if (register == null) return false;
        var lines = new ArrayList<Component>(); register.addToGoggleTooltip(lines, false);
        if (ticks == 100) {
            com.mojang.logging.LogUtils.getLogger().info("RETAIL_QA_WAIT stage={} hit={} lines={}", stage, mc.hitResult, lines.stream().map(Component::getString).toList());
            server(mc, () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                try {
                    var field = com.rexhub.createapparel.fashion.RetailInspection.class.getDeclaredField("WATCHING"); field.setAccessible(true);
                    com.mojang.logging.LogUtils.getLogger().info("RETAIL_QA_SERVER valid={} disconnected={} watched={} pick={}",
                            com.rexhub.createapparel.network.RetailInspectionRequestPacket.canObserve(player, REGISTER), player.hasDisconnected(),
                            ((java.util.Map<?, ?>) field.get(null)).get(player), player.pick(16, 0, false));
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            });
        }
        if (stage == 0) {
            if (!has(lines, "open") || !has(lines, "missing_stock") || has(lines, "checking")) return false;
            if (!(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(REGISTER)) throw new AssertionError("The real cursor is not pointing at the register");
            var outlines = Outliner.getInstance().getOutlines();
            if (!outlines.containsKey(new ClientRetailView.LinkOutline("apparel_retail_seat")) || !outlines.containsKey(new ClientRetailView.LinkOutline(new ClientRetailView.StorageOutline(0)))
                    || !outlines.containsKey(new ClientRetailView.LinkOutline(mc.level.getEntity(mannequinId)))
                    || !outlines.containsKey(new ClientRetailView.LinkOutline(mc.level.getBlockEntity(ADVERTISEMENT))))
                throw new AssertionError("Linked seat, storage, mannequin or advertisement highlight is missing");
            screenshot(mc, "retail-open-missing.png");
            server(mc, () -> ((ChestBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(STORAGE)).setItem(0, shirt().copyWithCount(4)));
            stage = 1; ticks = 0;
        } else if (stage == 1) {
            if (has(lines, "missing_stock") || has(lines, "checking")) return false;
            if (!has(lines, "open") || lines.size() != 3 || !lines.get(2).getString().contains(Component.translatable("tooltip.createapparel.register.connected", 1).getString()))
                throw new AssertionError("Restocking should leave exactly the three status rows and one connected storage");
            screenshot(mc, "retail-open-restocked.png");
            server(mc, () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                Objects.requireNonNull(mc.getSingleplayerServer().overworld().getEntity(cashierId)).discard();
            });
            stage = 2; ticks = 0;
        } else if (stage == 2) {
            if (!has(lines, "closed") || has(lines, "checking")) return false;
            if (!Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.LinkOutline("apparel_retail_seat"))) throw new AssertionError("An offhand phone should keep link highlights active");
            screenshot(mc, "retail-closed.png");
            server(mc, () -> ((CashRegisterBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(REGISTER)).linkStorage(STORAGE));
            stage = 3; ticks = 0;
        } else if (stage == 3) {
            if (!register.linkedStoragePositions().isEmpty() || !has(lines, "missing_stock") || has(lines, "checking")) return false;
            screenshot(mc, "retail-disconnected.png");
            server(mc, () -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
            stage = 4; ticks = 0;
        } else if (stage == 4) {
            if (mc.player.isHolding(ModItems.SMART_PHONE.get())) return false;
            if (Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.LinkOutline("apparel_retail_seat"))) throw new AssertionError("Highlights must clear after releasing the phone");
            if (!has(lines, "closed")) throw new AssertionError("Goggles should work without holding the phone");
            var tooltip = new ItemStack(FashionRegistries.CASH_REGISTER_ITEM.get()).getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
            if (tooltip.stream().anyMatch(line -> line.getString().contains(net.minecraft.client.resources.language.I18n.get("create.tooltip.keyShift"))))
                throw new AssertionError("The register still offers Shift details");
            var ui = PonderUI.of(FashionRegistries.CASH_REGISTER_ITEM.getId()); mc.setScreen(ui);
            if (!ui.getActiveScene().getId().getPath().equals("retail")) throw new AssertionError("The register does not open its retail Ponder");
            ui.seekToTime(230); stage = 5; ticks = 0;
        } else if (stage == 5) {
            if (!(mc.screen instanceof PonderUI ui)) throw new AssertionError("Retail Ponder closed unexpectedly");
            int[] cashiers = {0};
            ui.getActiveScene().forEachWorldEntity(net.minecraft.world.entity.npc.Villager.class, cashier -> {
                cashiers[0]++;
                if (!(cashier.getVehicle() instanceof SeatEntity seat)) throw new AssertionError("Ponder cashier is not riding its seat");
                double expectedY = seat.getY() + seat.getPassengersRidingOffset() + cashier.getMyRidingOffset() + SeatEntity.getCustomEntitySeatOffset(cashier);
                if (Math.abs(cashier.getY() - expectedY) > 0.001) throw new AssertionError("Ponder cashier is embedded below its riding position");
            });
            if (cashiers[0] != 1) throw new AssertionError("Ponder cashier is missing");
            screenshot(mc, "retail-ponder-cashier.png"); ui.seekToTime(530); stage = 6; ticks = 0;
        } else if (stage == 6) {
            if (!(mc.screen instanceof PonderUI ui)) throw new AssertionError("Retail Ponder closed unexpectedly");
            screenshot(mc, "retail-ponder-links.png");
            if (ui.getActiveScene().getTotalTime() > 650) throw new AssertionError("Removed Ponder explanation scenes are still present");
            ui.seekToTime(620);
            if (!ui.getActiveScene().isFinished()) throw new AssertionError("Retail Ponder did not finish");
            ui.seekToTime(0); ui.seekToTime(230); stage = 7; ticks = 0;
        } else if (stage == 7) {
            screenshot(mc, "retail-ponder-replay.png"); mc.setScreen(null);
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                var registerEntity = (CashRegisterBlockEntity) level.getBlockEntity(REGISTER);
                if (!registerEntity.linkedStoragePositions().contains(STORAGE) && !registerEntity.linkStorage(STORAGE))
                    throw new AssertionError("Retail language checks could not reconnect storage");
                lookAt(level.getServer().getPlayerList().getPlayers().get(0), Vec3.atCenterOf(REGISTER), new Vec3(-0.5, -60, -0.5));
            });
            mc.getLanguageManager().setSelected("ja_jp"); mc.options.languageCode = "ja_jp";
            reload = mc.reloadResourcePacks(); stage = 8; ticks = 0;
        } else if (stage == 8) {
            if (!reload.isDone()) return false;
            reload.join();
            if (has(lines, "checking") || !lines.get(2).getString().contains(Component.translatable("tooltip.createapparel.register.connected", 1).getString())) return false;
            if (!has(lines, "closed") || !lines.get(0).getString().strip().equals("閉店中")) throw new AssertionError("Japanese goggles status is missing");
            screenshot(mc, "retail-goggles-ja.png");
            var ui = PonderUI.of(FashionRegistries.CASH_REGISTER_ITEM.getId()); mc.setScreen(ui);
            if (!ui.getActiveScene().getTitle().equals(com.rexhub.createapparel.guide.RetailGuide.title(true))) throw new AssertionError("Japanese retail Ponder title is missing");
            ui.seekToTime(530); stage = 9; ticks = 0;
        } else if (stage == 9) {
            screenshot(mc, "retail-ponder-ja.png"); mc.setScreen(null);
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = level.getServer().getPlayerList().getPlayers().get(0);
                player.getInventory().selected = 0;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
                level.setBlock(STORAGE.east(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
                level.setBlock(STORAGE, left, 2);
                com.rexhub.createapparel.fashion.RetailLinkSession.start(player, InteractionHand.MAIN_HAND, ((CashRegisterBlockEntity) level.getBlockEntity(REGISTER)).reference());
                player.inventoryMenu.broadcastChanges();
            });
            stage = 10; ticks = 0;
        } else if (stage == 10) {
            if (register.linkedStoragePositions().size() != 2 || has(lines, "checking")) return false;
            if (register.linkedStorageBounds().get(0).getXsize() != 2
                    || !Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.LinkOutline(new ClientRetailView.StorageOutline(0))))
                throw new AssertionError("The large chest does not have a full-size storage highlight");
            if (!lines.get(2).getString().contains(Component.translatable("tooltip.createapparel.register.connected", 1).getString()))
                throw new AssertionError("A large chest should count as one connected storage");
            screenshot(mc, "retail-large-chest-highlight.png");
            server(mc, () -> mc.getSingleplayerServer().overworld().removeBlock(STORAGE, false));
            stage = 11; ticks = 0;
        } else if (stage == 11) {
            if (!register.linkedStoragePositions().equals(java.util.List.of(STORAGE.east())) || has(lines, "checking")) return false;
            if (register.linkedStorageBounds().get(0).getXsize() != 1) throw new AssertionError("The surviving chest highlight did not shrink");
            screenshot(mc, "retail-surviving-chest-highlight.png");
            mc.player.getInventory().selected = 1;
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
            mc.player.getInventory().selected = 0;
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
            stage = 12; ticks = 0;
        } else if (stage == 12) {
            server(mc, () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                if (com.rexhub.createapparel.fashion.RetailLinkSession.selected(player) != null)
                    throw new AssertionError("Real carried-slot packets did not clear the phone link session");
            });
            stage = 13; ticks = 0;
        } else if (stage == 13) {
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = level.getServer().getPlayerList().getPlayers().get(0);
                var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
                level.setBlock(STORAGE, left, 2);
                level.setBlock(STORAGE.east(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
                com.rexhub.createapparel.fashion.RetailLinkSession.start(player, InteractionHand.MAIN_HAND, ((CashRegisterBlockEntity) level.getBlockEntity(REGISTER)).reference());
                lookAt(player, Vec3.atCenterOf(STORAGE.east()), new Vec3(-7, -60, 3.5));
            });
            stage = 14; ticks = 0;
        } else if (stage == 14) {
            if (!pointsAt(mc, STORAGE.east()) || register.linkedStoragePositions().size() != 2) return false;
            var box = register.linkedStorageBounds(STORAGE.east());
            if (box == null || box.getXsize() != 2 || !Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.SettingOutline(box)))
                throw new AssertionError("The linked large chest under the real cursor is not highlighted during setup");
            assertOnlySettingOutline(); screenshot(mc, "retail-setting-storage.png");
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                var pos = STORAGE.west(2); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                lookAt(level.getServer().getPlayerList().getPlayers().get(0), Vec3.atCenterOf(pos), Vec3.atBottomCenterOf(pos.south(3)));
            });
            stage = 15; ticks = 0;
        } else if (stage == 15) {
            if (!pointsAt(mc, STORAGE.west(2))) return false;
            assertNoSettingOutline();
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                ((CashRegisterBlockEntity) level.getBlockEntity(REGISTER)).linkStorage(STORAGE.east());
                lookAt(level.getServer().getPlayerList().getPlayers().get(0), Vec3.atCenterOf(STORAGE.east()), new Vec3(-7, -60, 3.5));
            });
            stage = 16; ticks = 0;
        } else if (stage == 16) {
            if (!pointsAt(mc, STORAGE.east()) || !register.linkedStoragePositions().isEmpty()) return false;
            assertNoSettingOutline();
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                var panel = (AdvertisementPanelBlockEntity) level.getBlockEntity(ADVERTISEMENT.above());
                panel.controller().link(((CashRegisterBlockEntity) level.getBlockEntity(REGISTER)).reference());
                var facing = panel.getBlockState().getValue(com.rexhub.createapparel.advertisement.AdvertisementPanelBlock.FACING);
                lookAt(level.getServer().getPlayerList().getPlayers().get(0), Vec3.atCenterOf(panel.getBlockPos()),
                        Vec3.atBottomCenterOf(panel.getBlockPos().relative(facing, 3)).add(0, -1, 0));
            });
            stage = 17; ticks = 0;
        } else if (stage == 17) {
            if (!pointsAt(mc, ADVERTISEMENT.above())) return false;
            var controller = ((AdvertisementPanelBlockEntity) mc.level.getBlockEntity(ADVERTISEMENT.above())).controller();
            if (controller == null || !Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.SettingOutline(controller)))
                throw new AssertionError("A non-controller advertisement panel does not highlight its linked source");
            assertOnlySettingOutline(); screenshot(mc, "retail-setting-advertisement.png");
            server(mc, () -> {
                var level = mc.getSingleplayerServer().overworld();
                var mannequin = Objects.requireNonNull(level.getEntity(mannequinId));
                lookAt(level.getServer().getPlayerList().getPlayers().get(0), mannequin.getBoundingBox().getCenter(), mannequin.position().add(0, 0, 2.5));
            });
            stage = 18; ticks = 0;
        } else if (stage == 18) {
            if (!(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) || hit.getEntity().getId() != mannequinId) return false;
            if (!Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.SettingOutline(hit.getEntity())))
                throw new AssertionError("The linked mannequin under the real cursor is not highlighted during setup");
            assertOnlySettingOutline(); screenshot(mc, "retail-setting-mannequin.png");
            server(mc, () -> com.rexhub.createapparel.fashion.RetailLinkSession.clear(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)));
            stage = 19; ticks = 0;
        } else if (stage == 19) {
            assertNoSettingOutline();
            server(mc, () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
                player.inventoryMenu.broadcastChanges();
            });
            stage = 20; ticks = 0;
        } else if (stage == 20) {
            if (!mc.player.getOffhandItem().is(ModItems.SMART_PHONE.get())) return false;
            server(mc, () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                com.rexhub.createapparel.fashion.RetailLinkSession.start(player, InteractionHand.OFF_HAND,
                        ((CashRegisterBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(REGISTER)).reference());
            });
            stage = 21; ticks = 0;
        } else if (stage == 21) {
            if (!Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.SettingOutline(mc.level.getEntity(mannequinId))))
                throw new AssertionError("An offhand setup session does not highlight the hovered link");
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            stage = 22; ticks = 0;
        } else if (stage == 22) {
            assertNoSettingOutline(); mc.setScreen(null);
            stage = 23; ticks = 0;
        } else if (stage == 23) {
            if (!Outliner.getInstance().getOutlines().containsKey(new ClientRetailView.SettingOutline(mc.level.getEntity(mannequinId)))) {
                try {
                    var field = ClientRetailView.class.getDeclaredField("session"); field.setAccessible(true);
                    throw new AssertionError("Closing the screen did not restore the valid setup hover: hit=" + mc.hitResult + ", selected="
                            + mc.player.getInventory().selected + ", offhand=" + mc.player.getOffhandItem() + ", session=" + field.get(null));
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            }
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
            stage = 24; ticks = 0;
        } else if (stage == 24) {
            assertNoSettingOutline();
            com.mojang.logging.LogUtils.getLogger().info("RETAIL_CLIENT_QA_SUCCESS"); return true;
        }
        return false;
    }

    private static boolean pointsAt(Minecraft mc, BlockPos pos) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static void assertOnlySettingOutline() {
        var keys = Outliner.getInstance().getOutlines().keySet();
        if (keys.stream().anyMatch(ClientRetailView.LinkOutline.class::isInstance)
                || keys.stream().filter(ClientRetailView.SettingOutline.class::isInstance).count() != 1)
            throw new AssertionError("Setup should highlight only the hovered linked source");
    }

    private static void assertNoSettingOutline() {
        if (Outliner.getInstance().getOutlines().keySet().stream().anyMatch(ClientRetailView.SettingOutline.class::isInstance))
            throw new AssertionError("The setup hover outline did not clear");
    }

    private static void lookAt(net.minecraft.server.level.ServerPlayer player, Vec3 target, Vec3 standing) {
        var delta = target.subtract(standing.add(0, player.getEyeHeight(), 0));
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        player.connection.teleport(standing.x, standing.y, standing.z, yaw, pitch);
    }

    private static ItemStack shirt() {
        var stack = new ItemStack(ModItems.T_SHIRT.get());
        GarmentData.defaultFor(GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow()).write(stack);
        return stack;
    }

    private static boolean has(java.util.List<Component> lines, String suffix) {
        String text = Component.translatable("tooltip.createapparel.register." + suffix).getString();
        return lines.stream().anyMatch(line -> line.getString().strip().equals(text));
    }

    private static void server(Minecraft mc, Runnable action) {
        mc.getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable throwable) { failure = throwable; } });
    }

    private static void screenshot(Minecraft mc, String name) throws java.io.IOException {
        try (var pixels = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(Path.of(System.getProperty("apparel.fashionTest.projectDir"), "build", name));
        }
    }
}
