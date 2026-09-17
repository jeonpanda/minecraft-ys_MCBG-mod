package com.ys.ys_mcbg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * WorldBorder-ish "forcefield" cylinder (client render).
 * - Uses vanilla forcefield texture (no custom resource needed)
 * - Draws after translucent blocks
 * - Dynamic segments based on circumference (clamped)
 * - Adds thickness (two close radii) to reduce "gaps" at large radius
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientCircleBorderRender {

    // Vanilla forcefield texture
    private static final ResourceLocation FORCEFIELD = new ResourceLocation("textures/misc/forcefield.png");

    // ====== TUNING ======
    // How "dense" the wall should be along circumference (blocks per segment).
    // Smaller = smoother but heavier.
    private static final float ARC_STEP_BLOCKS = 1.5f;

    private static final int MIN_SEGMENTS = 64;
    private static final int MAX_SEGMENTS = 2048;

    // Thickness (in blocks)
    private static final float THICKNESS = 2.5f;

    // Tint + alpha
    private static final int CR = 120, CG = 210, CB = 255, CA = 120;

    // UV scroll speeds (worldborder-ish movement)
    private static final float U_SCROLL_SPEED = 0;
    private static final float V_SCROLL_SPEED = 0;

    // UV scale (texture repetition)
    private static final float U_TILE_AROUND = 80.0f;
    private static final float V_TILE_HEIGHT = 80.0f;

    private static final float MIN_RENDER_RADIUS = 1.0f;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        ClientZoneCache.Sample s = ClientZoneCache.sample();
        if (!s.running) return;

        float radius = s.r;
        if (radius < MIN_RENDER_RADIUS) return;

        double cx = s.cx;
        double cz = s.cz;

        // Cylinder from bottom to top of world
        float yBottom = level.getMinBuildHeight();
        float yTop = level.getMaxBuildHeight();

        int segments = calcSegments(radius);

        float halfT = THICKNESS * 0.5f;
        float rOuter = radius + halfT;
        float rInner = Math.max(0.01f, radius - halfT +1);

        Camera cam = mc.gameRenderer.getMainCamera();
        PoseStack ps = event.getPoseStack();

        double camX = cam.getPosition().x;
        double camY = cam.getPosition().y;
        double camZ = cam.getPosition().z;

        ps.pushPose();
        ps.translate(-camX, -camY, -camZ);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer c = buffers.getBuffer(RenderType.entityTranslucent(FORCEFIELD));
        final int FULL_BRIGHT = 0xF000F0;

        float t = (level.getGameTime() + mc.getFrameTime());
        float uScroll = 0;
        float vScroll = 0;

        //drawCylinder(c, ps, (float) cx, (float) cz, rOuter, yBottom, yTop, segments, uScroll, vScroll, FULL_BRIGHT);
        drawCylinder(c, ps, (float) cx, (float) cz, rInner, yBottom, yTop, segments, uScroll, vScroll, FULL_BRIGHT);

        buffers.endBatch();
        ps.popPose();
    }

    private static int calcSegments(float radius) {
        double circumference = 2.0 * Math.PI * radius;
        int seg = (int) Math.ceil(circumference / ARC_STEP_BLOCKS);
        if (seg < MIN_SEGMENTS) seg = MIN_SEGMENTS;
        if (seg > MAX_SEGMENTS) seg = MAX_SEGMENTS;
        return seg;
    }

    private static void drawCylinder(VertexConsumer c, PoseStack ps,
                                     float cx, float cz,
                                     float r,
                                     float yB, float yT,
                                     int segments,
                                     float uScroll, float vScroll,
                                     int light) {

        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0) * i / segments;
            double a1 = (Math.PI * 2.0) * (i + 1) / segments;

            float x0 = (float) (cx + Math.cos(a0) * r);
            float z0 = (float) (cz + Math.sin(a0) * r);
            float x1 = (float) (cx + Math.cos(a1) * r);
            float z1 = (float) (cz + Math.sin(a1) * r);

            float u0 = (i / (float) segments) * U_TILE_AROUND + uScroll;
            float u1 = ((i + 1) / (float) segments) * U_TILE_AROUND + uScroll;

            float v0 = 0.0f + vScroll;
            float v1 = V_TILE_HEIGHT + vScroll;

            // Quad (two triangles) - draw both windings to appear from inside/outside
            tri(c, ps, x0, yB, z0, u0, v0, light,
                    x0, yT, z0, u0, v1, light,
                    x1, yT, z1, u1, v1, light);

            tri(c, ps, x0, yB, z0, u0, v0, light,
                    x1, yT, z1, u1, v1, light,
                    x1, yB, z1, u1, v0, light);

            tri(c, ps, x1, yT, z1, u1, v1, light,
                    x0, yT, z0, u0, v1, light,
                    x0, yB, z0, u0, v0, light);

            tri(c, ps, x1, yB, z1, u1, v0, light,
                    x1, yT, z1, u1, v1, light,
                    x0, yB, z0, u0, v0, light);
        }
    }

    private static void tri(VertexConsumer c, PoseStack ps,
                            float x1, float y1, float z1, float u1, float v1, int light1,
                            float x2, float y2, float z2, float u2, float v2, int light2,
                            float x3, float y3, float z3, float u3, float v3, int light3) {
        vtx(c, ps, x1, y1, z1, u1, v1, light1);
        vtx(c, ps, x2, y2, z2, u2, v2, light2);
        vtx(c, ps, x3, y3, z3, u3, v3, light3);
    }

    private static void vtx(VertexConsumer c, PoseStack ps,
                            float x, float y, float z,
                            float u, float v,
                            int light) {
        c.vertex(ps.last().pose(), x, y, z)
                .color(CR, CG, CB, CA)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(ps.last().normal(), 0.0f, 1.0f, 0.0f)
                .endVertex();
    }
}
