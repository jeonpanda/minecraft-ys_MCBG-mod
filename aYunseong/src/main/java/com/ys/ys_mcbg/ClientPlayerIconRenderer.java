package com.ys.ys_mcbg;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class ClientPlayerIconRenderer {

    private ClientPlayerIconRenderer() {}

    /** 테두리 원(stroke) */
    public static void drawRing(GuiGraphics gg,
                                float cx, float cy,
                                float radius, float thickness,
                                int segments,
                                int argb) {

        if (radius <= 0.1f || thickness <= 0.1f) return;

        float outer = radius;
        float inner = Math.max(0f, radius - thickness);

        beginColor(gg);

        Matrix4f mat = gg.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        float start = (float) (-Math.PI / 2.0);
        float end = start + (float) (Math.PI * 2.0);

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float ang = Mth.lerp(t, start, end);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);

            buf.vertex(mat, cx + cos * outer, cy + sin * outer, 0).color(r, g, b, a).endVertex();
            buf.vertex(mat, cx + cos * inner, cy + sin * inner, 0).color(r, g, b, a).endVertex();
        }

        tess.end();
        endColor();
    }

    /** 채워진 도넛(annulus): innerR~outerR */
    public static void drawAnnulusFill(GuiGraphics gg,
                                       float cx, float cy,
                                       float innerR, float outerR,
                                       int segments,
                                       int argb) {

        if (outerR <= innerR + 0.1f) return;
        if (outerR <= 0.1f) return;

        beginColor(gg);

        Matrix4f mat = gg.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        float start = (float) (-Math.PI / 2.0);
        float end = start + (float) (Math.PI * 2.0);

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float ang = Mth.lerp(t, start, end);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);

            buf.vertex(mat, cx + cos * outerR, cy + sin * outerR, 0).color(r, g, b, a).endVertex();
            buf.vertex(mat, cx + cos * innerR, cy + sin * innerR, 0).color(r, g, b, a).endVertex();
        }

        tess.end();
        endColor();
    }

    /**
     * 플레이어 마커:
     * - 삼각형(방향) 먼저
     * - 노란 원(채움)이 위
     * - 중앙 흰 점
     */
    public static void drawPlayerMarker(GuiGraphics gg,
                                    float cx, float cy,
                                    float yawDeg,
                                    float size,
                                    int circleArgb,
                                    int triArgb,
                                    int dotArgb) {

    // 방향(삼각형) 먼저(조금 더 큼)
    drawDirectionTriangle(gg, cx, cy, yawDeg, size + 1.8f, triArgb);

    // 노란 원(채움)
    drawFilledCircle(gg, cx, cy, size, 40, circleArgb);

    // ✅ 가독성 강화: 외곽 검은 링 + 얇은 흰 링(자기 아이콘이 배경에 묻히지 않게)
    drawRing(gg, cx, cy, size + 1.25f, 1.25f, 64, 0xCC000000);
    drawRing(gg, cx, cy, size + 0.55f, 0.55f, 64, 0xAAFFFFFF);

    // 중앙 흰 점
    drawFilledCircle(gg, cx, cy, Math.max(1.0f, size * 0.33f), 24, dotArgb);
}

/** 작은 점(채움 원) - 미니맵/전체맵에서 다른 플레이어 표시용 */
    public static void drawDot(GuiGraphics gg, float cx, float cy, float radius, int argb) {
        drawFilledCircle(gg, cx, cy, radius, 24, argb);
    }

    /**
     * 작은 점(채움 원) + 테두리(바깥쪽 링)
     * - radius: 점(채움) 반경
     * - outlineThickness: 테두리 두께(바깥쪽으로 그려짐)
     */
    public static void drawOutlinedDot(GuiGraphics gg,
                                      float cx, float cy,
                                      float radius,
                                      float outlineThickness,
                                      int fillArgb,
                                      int outlineArgb) {
        drawFilledCircle(gg, cx, cy, radius, 24, fillArgb);

        if (outlineThickness > 0.1f) {
            // 바깥쪽 링: outerR = radius + outlineThickness, innerR = radius
            drawRing(gg, cx, cy, radius + outlineThickness, outlineThickness, 48, outlineArgb);
        }
    }

    // ─────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────

    private static void drawFilledCircle(GuiGraphics gg, float cx, float cy, float radius, int segments, int argb) {
        if (radius <= 0.1f) return;

        beginColor(gg);

        Matrix4f mat = gg.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        buf.vertex(mat, cx, cy, 0).color(r, g, b, a).endVertex();

        float start = (float) (-Math.PI / 2.0);
        float end = start + (float) (Math.PI * 2.0);

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float ang = Mth.lerp(t, start, end);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            buf.vertex(mat, cx + cos * radius, cy + sin * radius, 0).color(r, g, b, a).endVertex();
        }

        tess.end();
        endColor();
    }

    private static void drawDirectionTriangle(GuiGraphics gg, float cx, float cy, float yawDeg, float size, int argb) {
        // 화면 기준 "위쪽이 북쪽" 느낌: yaw-180
        float ang = (float) Math.toRadians(yawDeg - 180f);

        float len = size * 1.55f;
        float back = size * 0.85f;
        float halfW = size * 0.72f;

        float fx = (float) Math.sin(ang);
        float fy = (float) -Math.cos(ang);

        float lx = -fy;
        float ly = fx;

        float tipX = cx + fx * len;
        float tipY = cy + fy * len;

        float baseX = cx - fx * back;
        float baseY = cy - fy * back;

        float p2x = baseX + lx * halfW;
        float p2y = baseY + ly * halfW;

        float p3x = baseX - lx * halfW;
        float p3y = baseY - ly * halfW;

        beginColor(gg);

        Matrix4f mat = gg.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        buf.vertex(mat, tipX, tipY, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, p2x, p2y, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, p3x, p3y, 0).color(r, g, b, a).endVertex();

        tess.end();
        endColor();
    }

    private static void beginColor(GuiGraphics gg) {
        gg.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ✅ GUI 도형 안정화(사라짐 방지)
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void endColor() {
        // ✅ GUI 기본에 가깝게 유지
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
