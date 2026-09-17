package com.ys.ys_mcbg;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Stable team-only X-Ray / silhouette renderer.
 *
 * Important design choice:
 * We do NOT render PlayerRenderer a second time. That was the cause of the
 * duplicated-model / trembling effect in the previous implementation.
 *
 * Instead, a lightweight blocky humanoid silhouette is drawn with a depth-test
 * disabled POSITION_COLOR shader. The server supplies teammate positions up
 * to 600 blocks away, so this continues to work even when vanilla player
 * entity tracking is no longer active.
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTeamXrayRenderer {
    private static final double MAX_DISTANCE_SQR = 600.0D * 600.0D;

    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    // ====== TEAM MARKER BOX SIZE ======
    // Change these three values to resize the teammate box.
    // Width = X size, Height = Y size, Depth = Z size.
    private static final double BOX_WIDTH = 0.40D;
    private static final double BOX_HEIGHT = 1.70D;
    private static final double BOX_DEPTH = 0.40D;
    // ==================================

    private ClientTeamXrayRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player local = mc.player;
        if (level == null || local == null || local.isSpectator()) return;

        String myTeam = ClientTeamCache.getTeam(local.getUUID());
        if (myTeam == null || myTeam.isEmpty()) return;

        Iterable<ClientTeamMarkerCache.MarkerState> markers = ClientTeamMarkerCache.values();

        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        renderMarkerSilhouettes(mc, level, local, poseStack, markers, myTeam);

        poseStack.popPose();
    }

    private static void renderMarkerSilhouettes(
            Minecraft mc,
            Level level,
            Player local,
            PoseStack ps,
            Iterable<ClientTeamMarkerCache.MarkerState> markers,
            String myTeam) {

        // A single box is used for the teammate marker. When the target is
        // partly/fully hidden, the translucent fill becomes more noticeable.
        drawFillLayer(mc, level, local, ps, markers, myTeam);

        // Broad soft outer glow.
        drawLineLayer(mc, level, local, ps, markers, myTeam, 5.0f, 45);

        // Crisp team-colored outline.
        drawLineLayer(mc, level, local, ps, markers, myTeam, 2.0f, 235);
    }

    private static void drawFillLayer(
            Minecraft mc,
            Level level,
            Player local,
            PoseStack ps,
            Iterable<ClientTeamMarkerCache.MarkerState> markers,
            String myTeam) {

        beginColorRendering(false, 1.0f);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        boolean wrote = false;
        for (ClientTeamMarkerCache.MarkerState marker : markers) {
            UUID playerId = marker.playerId;
            double dx = marker.x() - local.getX();
            double dy = marker.y() - local.getY();
            double dz = marker.z() - local.getZ();
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQR) continue;

            String team = ClientTeamCache.getTeam(playerId);
            if (!myTeam.equals(team)) continue;

            Player live = level.getPlayerByUUID(playerId);
            if (live != null && live.isInvisible()) continue;

            int visibility = live == null ? 1 : getVisibilitySamples(local, live);
            if (visibility >= 8) continue;

            int color = ClientTeamCache.getTeamColor(myTeam, DEFAULT_COLOR);
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;

            // Slightly stronger fill when completely hidden, but still
            // translucent enough to feel like a PUBG-style visual aid.
            int alpha = visibility == 0 ? 58 : 35;
            wrote |= addMarkerBoxFaces(buf, marker.x(), marker.y(), marker.z(),
                    r, g, b, alpha, ps.last().pose());
        }

        tess.end();
        endColorRendering();
    }

    private static void drawLineLayer(
            Minecraft mc,
            Level level,
            Player local,
            PoseStack ps,
            Iterable<ClientTeamMarkerCache.MarkerState> markers,
            String myTeam,
            float lineWidth,
            int alpha) {

        beginColorRendering(true, lineWidth);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        boolean wrote = false;
        for (ClientTeamMarkerCache.MarkerState marker : markers) {
            UUID playerId = marker.playerId;

            double dx = marker.x() - local.getX();
            double dy = marker.y() - local.getY();
            double dz = marker.z() - local.getZ();
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQR) continue;

            String team = ClientTeamCache.getTeam(playerId);
            if (!myTeam.equals(team)) continue;

            Player live = level.getPlayerByUUID(playerId);
            if (live != null && live.isInvisible()) continue;

            int color = ClientTeamCache.getTeamColor(myTeam, DEFAULT_COLOR);
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;

            wrote |= addMarkerBoxEdges(buf, marker.x(), marker.y(), marker.z(),
                    r, g, b, alpha, ps.last().pose());
        }

        tess.end();
        endColorRendering();
    }

    /** Adds one stable cuboid around the teammate. */
    private static boolean addMarkerBoxEdges(
            BufferBuilder buf,
            double x, double y, double z,
            int r, int g, int b, int a,
            Matrix4f matrix) {
        // Player feet are approximately at marker.y.
        // Keep the box axis-aligned so it never shakes with player animation/yaw.
        addBoxEdges(buf, matrix,
                x, y + BOX_HEIGHT * 0.5D, z,
                BOX_WIDTH * 0.5D, BOX_HEIGHT * 0.5D, BOX_DEPTH * 0.5D,
                0.0F, r, g, b, a);
        return true;
    }

    private static boolean addMarkerBoxFaces(
            BufferBuilder buf,
            double x, double y, double z,
            int r, int g, int b, int a,
            Matrix4f matrix) {
        addBoxFaces(buf, matrix,
                x, y + BOX_HEIGHT * 0.5D, z,
                BOX_WIDTH * 0.5D, BOX_HEIGHT * 0.5D, BOX_DEPTH * 0.5D,
                0.0F, r, g, b, a);
        return true;
    }

    private static void addBoxEdges(
            BufferBuilder buf, Matrix4f matrix,
            double cx, double cy, double cz,
            double hx, double hy, double hz,
            float yaw,
            int r, int g, int b, int a) {

        Vec3[] p = boxPoints(cx, cy, cz, hx, hy, hz, yaw);
        int[][] edges = {
                {0,1},{1,2},{2,3},{3,0},
                {4,5},{5,6},{6,7},{7,4},
                {0,4},{1,5},{2,6},{3,7}
        };
        for (int[] e : edges) {
            vertex(buf, matrix, p[e[0]], r, g, b, a);
            vertex(buf, matrix, p[e[1]], r, g, b, a);
        }
    }

    private static void addBoxFaces(
            BufferBuilder buf, Matrix4f matrix,
            double cx, double cy, double cz,
            double hx, double hy, double hz,
            float yaw,
            int r, int g, int b, int a) {

        Vec3[] p = boxPoints(cx, cy, cz, hx, hy, hz, yaw);
        quad(buf, matrix, p[0], p[1], p[2], p[3], r, g, b, a);
        quad(buf, matrix, p[4], p[7], p[6], p[5], r, g, b, a);
        quad(buf, matrix, p[0], p[4], p[5], p[1], r, g, b, a);
        quad(buf, matrix, p[1], p[5], p[6], p[2], r, g, b, a);
        quad(buf, matrix, p[2], p[6], p[7], p[3], r, g, b, a);
        quad(buf, matrix, p[4], p[0], p[3], p[7], r, g, b, a);
    }

    private static Vec3[] boxPoints(
            double cx, double cy, double cz,
            double hx, double hy, double hz,
            float yawDeg) {

        double yaw = Math.toRadians(-yawDeg);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double[][] local = {
                {-hx,-hy,-hz},{ hx,-hy,-hz},{ hx,-hy, hz},{-hx,-hy, hz},
                {-hx, hy,-hz},{ hx, hy,-hz},{ hx, hy, hz},{-hx, hy, hz}
        };

        Vec3[] out = new Vec3[8];
        for (int i = 0; i < local.length; i++) {
            double lx = local[i][0];
            double lz = local[i][2];
            double rx = lx * cos - lz * sin;
            double rz = lx * sin + lz * cos;
            out[i] = new Vec3(cx + rx, cy + local[i][1], cz + rz);
        }
        return out;
    }

    private static void quad(
            BufferBuilder buf, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            int r, int g, int bl, int alpha) {
        vertex(buf, matrix, a, r, g, bl, alpha);
        vertex(buf, matrix, b, r, g, bl, alpha);
        vertex(buf, matrix, c, r, g, bl, alpha);
        vertex(buf, matrix, d, r, g, bl, alpha);
    }

    private static void vertex(
            BufferBuilder buf, Matrix4f matrix,
            Vec3 p, int r, int g, int b, int a) {
        buf.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .endVertex();
    }

    private static int getVisibilitySamples(Player viewer, Player target) {
        AABB box = target.getBoundingBox().inflate(-0.035D);
        double minX = box.minX;
        double maxX = box.maxX;
        double minY = box.minY;
        double maxY = box.maxY;
        double minZ = box.minZ;
        double maxZ = box.maxZ;

        Vec3 center = new Vec3((minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D);
        Vec3[] samples = {
                center,
                new Vec3(center.x, maxY - 0.08D, center.z),
                new Vec3(minX, center.y, minZ),
                new Vec3(minX, center.y, maxZ),
                new Vec3(maxX, center.y, minZ),
                new Vec3(maxX, center.y, maxZ),
                new Vec3(center.x, minY + 0.15D, center.z),
                new Vec3(center.x, minY + 1.25D, center.z)
        };

        int visible = 0;
        Vec3 camera = viewer.getEyePosition(1.0F);
        for (Vec3 sample : samples) {
            ClipContext clip = new ClipContext(
                    camera,
                    sample,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    viewer
            );
            HitResult result = target.level().clip(clip);
            if (result.getType() == HitResult.Type.MISS) visible++;
        }
        return visible;
    }

    private static void beginColorRendering(boolean lines, float lineWidth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (lines) {
            RenderSystem.lineWidth(lineWidth);
        }
    }

    private static void endColorRendering() {
        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
