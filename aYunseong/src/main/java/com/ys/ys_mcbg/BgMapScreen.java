package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import static com.ys.ys_mcbg.BgMapConstants.*;

public class BgMapScreen extends Screen {

    // Zoom is per-screen instance and resets every time the screen is opened.
    private float zoom = 1.0f;   // 1.0 = full map
    private float panX = 0.0f;   // texture-space (0..MAP_SIZE) top-left
    private float panY = 0.0f;

    private static final float ZOOM_STEP = 1.15f;
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 8.0f;

    public BgMapScreen() {
        super(Component.literal("MCBG Map"));
        // reset on open
        this.zoom = 1.0f;
        this.panX = 0.0f;
        this.panY = 0.0f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_M) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!ClientMapCache.ready || ClientMapCache.getTexId() == null) return super.mouseScrolled(mouseX, mouseY, delta);

        // Compute current draw rect (same math as render)
        int w = this.width;
        int h = this.height;

        float baseScale = Math.min((w - 20) / (float) MAP_SIZE, (h - 20) / (float) MAP_SIZE);
        baseScale = Mth.clamp(baseScale, 0.1f, 2.0f);

        int drawW = (int) (MAP_SIZE * baseScale);
        int drawH = (int) (MAP_SIZE * baseScale);

        int x0 = (w - drawW) / 2;
        int y0 = (h - drawH) / 2;

        // Only zoom when mouse is over the map
        if (mouseX < x0 || mouseX >= x0 + drawW || mouseY < y0 || mouseY >= y0 + drawH) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        float oldZoom = this.zoom;
        float newZoom = oldZoom * (delta > 0 ? ZOOM_STEP : (1.0f / ZOOM_STEP));
        newZoom = Mth.clamp(newZoom, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(newZoom - oldZoom) < 1e-4f) return true;

        int oldView = Math.max(1, (int) (MAP_SIZE / oldZoom));
        int newView = Math.max(1, (int) (MAP_SIZE / newZoom));

        float relX = (float) ((mouseX - x0) / (double) drawW); // 0..1
        float relY = (float) ((mouseY - y0) / (double) drawH);

        // Keep the texture coordinate under the cursor stable.
        float focusX = this.panX + relX * oldView;
        float focusY = this.panY + relY * oldView;

        float newPanX = focusX - relX * newView;
        float newPanY = focusY - relY * newView;

        this.zoom = newZoom;
        this.panX = clampPan(newPanX, newView);
        this.panY = clampPan(newPanY, newView);
        return true;
    }

    private static float clampPan(float pan, int view) {
        float max = MAP_SIZE - view;
        if (max < 0) max = 0;
        return Mth.clamp(pan, 0.0f, max);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);

        if (!ClientMapCache.ready || ClientMapCache.getTexId() == null) {
            gg.drawString(Minecraft.getInstance().font, "맵 로딩중...", 10, 10, 0xFFFFFF, true);
            super.render(gg, mouseX, mouseY, partialTick);
            return;
        }

        int w = this.width;
        int h = this.height;

        // Base scale that fits the full MAP_SIZE to the screen.
        float baseScale = Math.min((w - 20) / (float) MAP_SIZE, (h - 20) / (float) MAP_SIZE);
        baseScale = Mth.clamp(baseScale, 0.1f, 2.0f);

        int drawW = (int) (MAP_SIZE * baseScale);
        int drawH = (int) (MAP_SIZE * baseScale);

        int x0 = (w - drawW) / 2;
        int y0 = (h - drawH) / 2;

        // Zoom window in texture space
        int view = Math.max(1, (int) (MAP_SIZE / this.zoom));
        this.panX = clampPan(this.panX, view);
        this.panY = clampPan(this.panY, view);
        int srcX = (int) this.panX;
        int srcY = (int) this.panY;

        // Final screen-space scale per texture pixel (after zoom)
        float mapScale = (drawW / (float) view);

        // 1) Map texture (draw a sub-rect and scale it up)
        gg.pose().pushPose();
        gg.pose().translate(x0, y0, 0);

        // scale inside so that 'view' pixels fill MAP_SIZE pixels
        float zoomScale = MAP_SIZE / (float) view; // ~zoom
        gg.pose().scale(baseScale * zoomScale, baseScale * zoomScale, 1f);

        gg.blit(ClientMapCache.getTexId(), 0, 0, srcX, srcY, view, view, MAP_SIZE, MAP_SIZE);
        gg.pose().popPose();

        gg.flush();

        // Clip to map rect
        gg.enableScissor(x0, y0, x0 + drawW, y0 + drawH);

        // 2) Zone rings (map-space -> screen-space via src offset)
        var zs = ClientZoneCache.sample();
        if (zs.running && zs.r > 0.5f) {
            float curMx = (float) (zs.cx + HALF);
            float curMz = (float) (zs.cz + HALF);

            float curSx = x0 + (curMx - srcX) * mapScale;
            float curSy = y0 + (curMz - srcY) * mapScale;
            float curSr = zs.r * mapScale;

            float outerR = maxCornerDist(curSx, curSy, x0, y0, drawW, drawH) + 4.0f;

            // outside fill
            ClientPlayerIconRenderer.drawAnnulusFill(
                    gg,
                    curSx, curSy,
                    curSr,
                    outerR,
                    256,
                    0x552D6BFF
            );

            // ✅ show target? (1페이즈 WAIT에서는 숨김, 1페이즈 SHRINK부터 보임, 2~는 항상 보임)
            boolean showTarget =
                    (ClientZoneCache.phase >= 2) ||
                            (ClientZoneCache.phase == 1 && ClientZoneCache.stage == ZoneRuntime.STAGE_SHRINK);

            // ✅ next/target (white) - 먼저 그려서 아래 레이어로
            if (showTarget && (ClientZoneCache.stage == ZoneRuntime.STAGE_WAIT || ClientZoneCache.stage == ZoneRuntime.STAGE_SHRINK)) {
                float tr = (float) ClientZoneCache.toR;
                float tx = (float) (ClientZoneCache.toX + HALF);
                float tz = (float) (ClientZoneCache.toZ + HALF);

                float tsx = x0 + (tx - srcX) * mapScale;
                float tsy = y0 + (tz - srcY) * mapScale;

                if (tr <= 0.5f) {
                    ClientPlayerIconRenderer.drawRing(
                            gg,
                            tsx, tsy,
                            2.0f,
                            2.0f,
                            48,
                            0xFFFFFFFF
                    );
                } else {
                    ClientPlayerIconRenderer.drawRing(
                            gg,
                            tsx, tsy,
                            tr * mapScale,
                            2.0f,
                            256,
                            0xFFFFFFFF
                    );
                }
            }

            // ✅ current (blue) - 마지막에 그려서 위 레이어로
            ClientPlayerIconRenderer.drawRing(
                    gg,
                    curSx,
                    curSy,
                    curSr,
                    2.0f,
                    256,
                    0xFF2D6BFF
            );
        }

        // ✅ other players: 같은 팀만 표시 (점 + 테두리) + 전체맵에서만 아이디 표기
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            String myTeam = ClientTeamCache.getTeam(mc.player.getUUID());
            if (myTeam != null) {
                for (var p2 : mc.level.players()) {
                    if (p2 == mc.player) continue;

                    String t = ClientTeamCache.getTeam(p2.getUUID());
                    if (t == null || !t.equals(myTeam)) continue;

                    float pmx2 = (float) (p2.getX() + HALF);
                    float pmz2 = (float) (p2.getZ() + HALF);
                    float sx2 = x0 + (pmx2 - srcX) * mapScale;
                    float sy2 = y0 + (pmz2 - srcY) * mapScale;

                    if (sx2 < x0 - 8 || sy2 < y0 - 8 || sx2 > x0 + drawW + 8 || sy2 > y0 + drawH + 8) continue;

                    int color = ClientTeamCache.getTeamColor(t, 0xFF33FF66);
                    float dotR = 3.0f;
                    float outline = 1.0f;
                    ClientPlayerIconRenderer.drawOutlinedDot(gg, sx2, sy2, dotR, outline, color, 0xFF000000);

                    // 아이디(가독성: 반투명 배경 + 그림자) - 전체맵 전용(미니맵에는 표시하지 않음)
String name = p2.getGameProfile().getName();
float nameScale = 0.60f;

gg.pose().pushPose();
gg.pose().translate(sx2, sy2 + dotR + 3.0f, 0);
gg.pose().scale(nameScale, nameScale, 1.0f);

int tw = mc.font.width(name);
int tx = -tw / 2;
int ty = 0;

gg.fill(tx - 2, ty - 1, tx + tw + 2, ty + mc.font.lineHeight, 0xAA000000);
gg.drawString(mc.font, name, tx, ty, 0xFFFFFFFF, true);

gg.pose().popPose();
}
            }
        }
        // 3) Player marker (do NOT change size)
        if (mc.player != null) {
            float pmx = (float) (mc.player.getX() + HALF);
            float pmz = (float) (mc.player.getZ() + HALF);

            float sx = x0 + (pmx - srcX) * mapScale;
            float sy = y0 + (pmz - srcY) * mapScale;

            // Clamp into map rect so it never disappears
            float pad = 3.0f;
            sx = Mth.clamp(sx, x0 + pad, x0 + drawW - pad);
            sy = Mth.clamp(sy, y0 + pad, y0 + drawH - pad);

            gg.pose().pushPose();
            gg.pose().translate(0, 0, 50);

            ClientPlayerIconRenderer.drawPlayerMarker(
                    gg,
                    sx, sy,
                    mc.player.getYRot(),
                    3.4f,
                    0xFFFFD400,
                    0xCCFFFFFF,
                    0xFFFFFFFF
            );

            gg.pose().popPose();
        }

        gg.disableScissor();

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private static float maxCornerDist(float cx, float cy, int x0, int y0, int w, int h) {
        float d1 = dist(cx, cy, x0, y0);
        float d2 = dist(cx, cy, x0 + w, y0);
        float d3 = dist(cx, cy, x0, y0 + h);
        float d4 = dist(cx, cy, x0 + w, y0 + h);
        return Math.max(Math.max(d1, d2), Math.max(d3, d4));
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
