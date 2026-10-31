package com.rexhub.createapparel.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EntityPreview {
    private EntityPreview() { }

    public static void render(GuiGraphics graphics, int x, int y, int scale,
                              float mouseX, float mouseY, LivingEntity entity) {
        float yaw = (float) Math.atan(mouseX / 40F);
        float pitch = (float) Math.atan(mouseY / 40F);
        var tilt = new Quaternionf().rotateX(pitch * 20F * (float) (Math.PI / 180));
        var pose = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
        float body = entity.yBodyRot, rotation = entity.getYRot(), head = entity.yHeadRot;
        float oldHead = entity.yHeadRotO, oldPitch = entity.getXRot();
        try {
            entity.yBodyRot = 180F + yaw * 20F;
            entity.setYRot(180F + yaw * 40F);
            entity.setXRot(-pitch * 20F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, new Vector3f(), pose, tilt, entity);
        } finally {
            entity.yBodyRot = body;
            entity.setYRot(rotation);
            entity.setXRot(oldPitch);
            entity.yHeadRot = head;
            entity.yHeadRotO = oldHead;
        }
    }
}
