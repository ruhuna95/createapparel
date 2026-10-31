package com.rexhub.createapparel.client.advertisement;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.CreateApparelClient;
import com.rexhub.createapparel.client.citizen.ClientCitizenAssets;
import com.rexhub.createapparel.client.garment.*;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.util.*;

public final class PhotoRenderCache {
    public record Image(ResourceLocation texture, int width, int height) { }
    private record Key(net.minecraft.nbt.CompoundTag visual, int width, int height, ResourceLocation skin, ArmProfile arms, long generation) { }
    private static final LinkedHashMap<Key, Image> IMAGES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<UUID, Skin> SKINS = new HashMap<>();
    private static final ByteBufferBuilder PHOTO_BUFFER = new ByteBufferBuilder(262144);
    private static final class Skin {
        ResourceLocation texture; ArmProfile arms; long nextRefresh;
        Skin(UUID id) { texture = DefaultPlayerSkin.get(id).texture(); arms = DefaultPlayerSkin.get(id).model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? ArmProfile.SLIM : ArmProfile.CLASSIC; }
    }
    private static long serial, lastGeneration = -1, lastFrame;
    private static int generated;
    private PhotoRenderCache() { }
    public static void clear() {
        for (var image : IMAGES.values()) releaseAfterFrame(image); IMAGES.clear(); SKINS.clear();
    }
    private static void releaseAfterFrame(Image image) {
        var minecraft = Minecraft.getInstance();
        minecraft.tell(() -> minecraft.getTextureManager().release(image.texture()));
    }
    @javax.annotation.Nullable public static Image image(AdvertisingPhotoData photo, int screenWidth, int screenHeight) {
        var minecraft = Minecraft.getInstance(); long generation = ClientGarmentAssets.INSTANCE.generation();
        if (generation != lastGeneration) { clear(); lastGeneration = generation; }
        int largest = Math.max(screenWidth, screenHeight);
        int unit = Math.max(1, 512 / Math.max(1, largest));
        int width = Math.max(1, screenWidth * unit), height = Math.max(1, screenHeight * unit);
        var skin = photo.playerId() == null ? null : skin(photo.playerId());
        var visual = photo.save(); visual.remove("PhotoId"); visual.remove("CaptureId");
        var key = new Key(visual, width, height, skin == null ? DefaultPlayerSkin.getDefaultTexture() : skin.texture,
                skin == null ? photo.appearance().armProfile() : skin.arms, generation);
        var cached = IMAGES.get(key); if (cached != null) return cached;
        long frame = minecraft.getFrameTimeNs();
        if (frame != lastFrame) { lastFrame = frame; generated = 0; }
        if (generated >= 1) return null; generated++;
        var result = render(photo, width, height, skin); IMAGES.put(key, result);
        while (IMAGES.size() > 32) { var iterator = IMAGES.entrySet().iterator(); var old = iterator.next().getValue(); iterator.remove(); releaseAfterFrame(old); }
        return result;
    }
    private static Skin skin(UUID id) {
        if (SKINS.size() >= 128 && !SKINS.containsKey(id)) SKINS.remove(SKINS.keySet().iterator().next());
        var skin = SKINS.computeIfAbsent(id, Skin::new); long now = net.minecraft.Util.getMillis();
        var player = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(id);
        if (player instanceof net.minecraft.client.player.AbstractClientPlayer client) {
            skin.texture = client.getSkin().texture(); skin.arms = client.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? ArmProfile.SLIM : ArmProfile.CLASSIC;
        }
        if (now >= skin.nextRefresh) {
            skin.nextRefresh = now + 60000;
            Minecraft.getInstance().getSkinManager().getOrLoad(new GameProfile(id, "")).thenAccept(loaded ->
                    Minecraft.getInstance().execute(() -> {
                        skin.texture = loaded.texture();
                        skin.arms = loaded.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? ArmProfile.SLIM : ArmProfile.CLASSIC;
                    }));
        }
        return skin;
    }
    private static Image render(AdvertisingPhotoData photo, int width, int height, @javax.annotation.Nullable Skin skin) {
        var minecraft = Minecraft.getInstance();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix()); var sorting = com.rexhub.createapparel.client.mixin.RenderSystemAccessor.createapparel$vertexSorting();
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        var previousShader = RenderSystem.getShader(); var previousColor = RenderSystem.getShaderColor().clone();
        int previousTexture = RenderSystem.getShaderTexture(0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var lightShader = GameRenderer.getRendertypeEntityCutoutNoCullShader(); RenderSystem.setupShaderLights(lightShader);
        var light0 = lightShader.LIGHT0_DIRECTION.getFloatBuffer(); var light1 = lightShader.LIGHT1_DIRECTION.getFloatBuffer();
        var previousLight0 = new org.joml.Vector3f(light0.get(0), light0.get(1), light0.get(2));
        var previousLight1 = new org.joml.Vector3f(light1.get(0), light1.get(1), light1.get(2));
        float[] clear = new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        TextureTarget target = null;
        try {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            RenderSystem.setShaderFogStart(1000000); RenderSystem.setShaderFogEnd(10000000);
            com.mojang.blaze3d.platform.GlStateManager._disableScissorTest(); RenderSystem.depthMask(true); RenderSystem.setShaderColor(1, 1, 1, 1);
            int rgb = photo.layout().background().tintRgb(); target.setClearColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity(); RenderSystem.applyModelViewMatrix(); RenderSystem.enableDepthTest(); RenderSystem.disableCull();
            RenderSystem.setShaderLights(new org.joml.Vector3f(0.2F, -0.5F, 1).normalize(),
                    new org.joml.Vector3f(-0.2F, 0.4F, 1).normalize());
            ArmProfile arms = skin == null ? photo.appearance().armProfile() : skin.arms;
            HumanoidModel<?> model = skin == null ? new CitizenModel(minecraft.getEntityModels().bakeLayer(CreateApparelClient.CITIZEN_LAYER))
                    : new PlayerModel<>(minecraft.getEntityModels().bakeLayer(arms == ArmProfile.SLIM ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), arms == ArmProfile.SLIM);
            pose(model, photo.pose());
            model.leftArm.y = model.rightArm.y = arms == ArmProfile.SLIM ? 2.5F : 2;
            if (model instanceof CitizenModel citizen) citizen.applyAge(photo.age());
            var loadout = photo.loadout(ClientGarmentAssets::getDefinition);
            var snapshot = new LoadoutRenderSnapshot(loadout, arms, photo.appearance().chestProfile());
            var bounds = new Bounds(); PoseStack measure = new PoseStack(); measure.mulPose(Axis.YP.rotationDegrees(180 + photo.layout().bodyYaw()));
            subject(photo, model, snapshot, measure, type -> bounds, skin);
            double minY = bounds.minY, maxY = bounds.maxY;
            if (photo.layout().framing() == com.rexhub.createapparel.photo.PhotoLayout.Framing.UPPER_BODY) maxY = Math.min(maxY, 0.8);
            if (photo.layout().framing() == com.rexhub.createapparel.photo.PhotoLayout.Framing.CLOSE_UP) maxY = Math.min(maxY, 0.12);
            var placement = PhotoLayoutMath.place(width, height, bounds.minX, minY, bounds.maxX, maxY, photo.layout());
            PoseStack pose = new PoseStack(); pose.translate(placement.x(), placement.y(), 0);
            pose.scale((float) placement.scale(), (float) placement.scale(), (float) placement.scale());
            if (photo.layout().mirror()) { pose.translate(bounds.minX + bounds.maxX, 0, 0); pose.scale(-1, 1, 1); }
            pose.mulPose(Axis.YP.rotationDegrees(180 + photo.layout().bodyYaw()));
            var buffers = MultiBufferSource.immediate(PHOTO_BUFFER); subject(photo, model, snapshot, pose, buffers, skin); buffers.endBatch();
            var location = ResourceLocation.fromNamespaceAndPath("createapparel", "advertising_photo/" + serial++);
            var texture = new TargetTexture(target); minecraft.getTextureManager().register(location, texture); target = null;
            return new Image(location, width, height);
        } finally {
            if (target != null) target.destroyBuffers();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setProjectionMatrix(projection, sorting); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask); RenderSystem.setShaderLights(previousLight0, previousLight1);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShader(() -> previousShader); RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShaderTexture(0, previousTexture); com.mojang.blaze3d.platform.GlStateManager._bindTexture(boundTexture);
            com.mojang.blaze3d.platform.GlStateManager._clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
        }
    }
    private static void pose(HumanoidModel<?> model, PosePreset preset) {
        var rotations = preset.rotations();
        rotate(model.head, rotations.head()); rotate(model.body, rotations.body()); rotate(model.leftArm, rotations.leftArm());
        rotate(model.rightArm, rotations.rightArm()); rotate(model.leftLeg, rotations.leftLeg()); rotate(model.rightLeg, rotations.rightLeg()); model.hat.copyFrom(model.head);
    }
    private static void rotate(net.minecraft.client.model.geom.ModelPart part, PosePreset.Rotation rotation) {
        part.setRotation(rotation.x() * net.minecraft.util.Mth.DEG_TO_RAD, rotation.y() * net.minecraft.util.Mth.DEG_TO_RAD, rotation.z() * net.minecraft.util.Mth.DEG_TO_RAD);
    }
    private static void subject(AdvertisingPhotoData photo, HumanoidModel<?> model, LoadoutRenderSnapshot snapshot, PoseStack pose,
                                MultiBufferSource buffers, @javax.annotation.Nullable Skin skin) {
        int light = LightTexture.FULL_BRIGHT, overlay = OverlayTexture.NO_OVERLAY; var appearance = photo.appearance();
        ClientGarmentAssets.INSTANCE.resolveBody(snapshot.armProfile(), appearance.chestProfile()).ifPresent(parts ->
                CommonGarmentRenderer.renderHumanoidSupport(parts, model, snapshot.armProfile(), appearance.skinTone().tintRgb(), pose, buffers, light, overlay));
        ClientGarmentAssets.INSTANCE.resolveDefaultUnderwear(appearance.chestProfile(), snapshot.garment(GarmentSlot.INNER_TOP).isEmpty()
                && snapshot.garment(GarmentSlot.TOP).isEmpty(), snapshot.garment(GarmentSlot.INNER_BOTTOM).isEmpty()).ifPresent(parts ->
                CommonGarmentRenderer.renderHumanoidSupport(parts, model, snapshot.armProfile(), 0xFFFFFF, pose, buffers, light, overlay));
        if (skin != null) {
            var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture)); model.head.render(pose, vertices, light, overlay); model.hat.render(pose, vertices, light, overlay);
        } else {
            var style = ClientCitizenAssets.INSTANCE.resolve(appearance.hairStyleId());
            var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(CitizenTextureCache.get(appearance, style)));
            model.head.render(pose, vertices, light, overlay);
            boolean covered = snapshot.garment(GarmentSlot.HEAD).isPresent();
            if (style.hatPolicy().showHat(covered)) model.hat.render(pose, vertices, light, overlay);
            if (style.hatPolicy().showExtra(covered)) style.geometry().ifPresent(extra -> extra.renderHeadAttached(pose, vertices, light, overlay, model.head, style.hatPolicy().showHat(covered)));
        }
        CommonGarmentRenderer.renderHumanoid(new GarmentRenderContext(model, snapshot), pose, buffers, light, overlay);
    }
    private static final class TargetTexture extends AbstractTexture {
        private final TextureTarget target;
        TargetTexture(TextureTarget target) { this.target = target; id = target.getColorTextureId(); }
        @Override public void load(ResourceManager resources) { }
        @Override public void close() { target.destroyBuffers(); id = -1; }
    }
    private static final class Bounds implements VertexConsumer {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        @Override public VertexConsumer addVertex(float x, float y, float z) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
