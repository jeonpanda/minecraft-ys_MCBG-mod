package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import static com.ys.ys_mcbg.BgMapConstants.*;

/**
 * 왼쪽 상단 미니맵 렌더러
 * - 맵은 플레이어 중심으로 스크롤
 * - 단, 맵 경계(클램프) 걸리면 플레이어 아이콘이 화면 중앙에서 가장자리로 이동 (PUBG 느낌)
 */
public final class MinimapRenderer {

    private MinimapRenderer() {}

    private static boolean expanded = false;

    /** N키로 확대/축소 토글 (크기 + 보이는 반경 둘 다 변경) */
    public static void toggleZoom() {
        expanded = !expanded;
        MINI_SIZE = expanded ? MINI_LARGE : MINI_SMALL;
        VIEW_PX = expanded ? VIEW_LARGE : VIEW_SMALL;
    }

    public static boolean isExpanded() {
        return expanded;
    }


    /** 미니맵 크기(px) */
    private static final int MINI_SMALL = 100;
    private static final int MINI_LARGE = 150;

    /** 미니맵 크기(px) */
    public static int MINI_SIZE = MINI_SMALL;

    /** 미니맵이 보여주는 월드(텍스처) 범위(px=블록). 값이 클수록 더 줌아웃 */
    private static final int VIEW_SMALL = 160;
    private static final int VIEW_LARGE = 260;

    /** 미니맵이 보여주는 월드(텍스처) 범위(px=블록). 값이 클수록 더 줌아웃 */
    public static int VIEW_PX = VIEW_SMALL;

    /** 화면 좌상단 앵커(여백) */
    public static int MARGIN_X = 6;
    public static int MARGIN_Y = 6;      // ✅ 위로 올림 (시간 글자 이동했으니)

    public static int getX0(Minecraft mc) {
        return MARGIN_X;
    }

    public static int getY0(Minecraft mc) {
        return MARGIN_Y;
    }

    public static void render(GuiGraphics gg) {
        if (!ClientMapCache.ready || ClientMapCache.getTexId() == null) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int x0 = getX0(mc);
        int y0 = getY0(mc);

        // 관전 중에는 실제 카메라(관전 대상)를 미니맵 중심으로 사용한다.
        var viewEntity = mc.getCameraEntity();
        if (viewEntity == null) viewEntity = mc.player;

        int px = Mth.floor(viewEntity.getX());
        int pz = Mth.floor(viewEntity.getZ());
        int mx = px + HALF;
        int mz = pz + HALF;

        // ideal crop
        int uIdeal = mx - VIEW_PX / 2;
        int vIdeal = mz - VIEW_PX / 2;

        // clamp crop
        int u = Mth.clamp(uIdeal, 0, MAP_SIZE - VIEW_PX);
        int v = Mth.clamp(vIdeal, 0, MAP_SIZE - VIEW_PX);

        // ✅ 플레이어 로컬 좌표 (클램프 걸리면 중앙에서 이동)
        float playerLocalX = (float) (mx - u);
        float playerLocalZ = (float) (mz - v);

        // view -> mini로 축소
        float s = MINI_SIZE / (float) VIEW_PX;

        // 미니맵 밖으로 나가는 모든 렌더링(원/마커 등) 클리핑
        gg.enableScissor(x0, y0, x0 + MINI_SIZE, y0 + MINI_SIZE);

        gg.pose().pushPose();
        gg.pose().translate(x0, y0, 0);
        gg.pose().scale(s, s, 1f);

        // 1) 맵 텍스처
        gg.blit(ClientMapCache.getTexId(), 0, 0, u, v, VIEW_PX, VIEW_PX, MAP_SIZE, MAP_SIZE);

        // ✅ blit 이후 도형/텍스처 섞일 때 순서 꼬임 방지
        gg.flush();

        // ─────────────────────────────────────────────────────────
        // 자기장 표시 (PUBG 스타일)
        //  - 현재 자기장: 파란 테두리 + 바깥영역 파란 반투명 채움
        //  - 다음(대기)/목표(축소중): 흰 테두리
        // ─────────────────────────────────────────────────────────
        var zs = ClientZoneCache.sample();
        if (zs.running && zs.r > 0.5f) {

            float curTexX = (float) (zs.cx + HALF);
            float curTexZ = (float) (zs.cz + HALF);

            float curLocalX = curTexX - u;
            float curLocalZ = curTexZ - v;

            // (A) 현재 자기장 바깥을 파란 반투명으로 채움 (스캐서로 미니맵 사각형 안에서만 보임)
            float outer = maxDistToViewCorners(curLocalX, curLocalZ, VIEW_PX);
            if (outer > zs.r + 0.5f) {
                float thicknessFill = outer - zs.r;
                ClientPlayerIconRenderer.drawRing(
                        gg,
                        curLocalX,
                        curLocalZ,
                        outer,
                        thicknessFill,
                        256,
                        0x552D6BFF   // ✅ 파란 반투명 (alpha 0x55)
                );
            }

            // (B) 현재 자기장 테두리 (파랑)
            float thicknessOutline = 2.0f / s; // 화면 2px 유지
            ClientPlayerIconRenderer.drawRing(
                    gg,
                    curLocalX,
                    curLocalZ,
                    zs.r,
                    thicknessOutline,
                    192,
                    0xFF2D6BFF
            );

            // (C) 다음/목표 자기장 테두리 (흰)
            if (ClientZoneCache.stage == ZoneRuntime.STAGE_WAIT || ClientZoneCache.stage == ZoneRuntime.STAGE_SHRINK) {
                float tr = (float) ClientZoneCache.toR;
                float tx = (float) (ClientZoneCache.toX + HALF);
                float tz = (float) (ClientZoneCache.toZ + HALF);

                float sx = (tx - u);
                float sy = (tz - v);

                float dx = tx - curTexX;
                float dz = tz - curTexZ;
                boolean different = (Math.abs(tr - zs.r) > 0.25f) || (dx * dx + dz * dz > 0.25f);
                boolean showTarget =
                        (ClientZoneCache.phase >= 2) ||
                                (ClientZoneCache.phase == 1 && ClientZoneCache.stage == ZoneRuntime.STAGE_SHRINK);

                // ✅ 1) 다음 원 반경이 거의 0이면: "작은 흰 원(점)"으로 표시
                if (tr <= 0.5f) {
                    float dotR = 3.5f; // 미니맵에서 보이기 좋은 픽셀 반경(조절 가능)

                    // 속 채우고 싶으면 (가능한 경우)
                    ClientPlayerIconRenderer.drawAnnulusFill(
                            gg,
                            sx, sy,
                            0.0f,
                            dotR,
                            64,
                            0xFFFFFFFF
                    );

                    // 테두리
                    ClientPlayerIconRenderer.drawRing(
                            gg,
                            sx, sy,
                            dotR,
                            thicknessOutline,
                            64,
                            0xFFFFFFFF
                    );
                }
                // ✅ 2) 일반적인 경우: 다음 원을 흰색 링으로 표시
                else if (different&&showTarget) {
                    ClientPlayerIconRenderer.drawRing(
                            gg,
                            sx, sy,
                            tr,
                            thicknessOutline,
                            192,
                            0xFFFFFFFF
                    );
                }
            }

        }

        // ✅ 다른 플레이어: 같은 팀만 표시(점 + 테두리)
        if (mc.level != null) {
            String myTeam = ClientTeamCache.getTeam(mc.player.getUUID());
            if (myTeam != null) {
                // 화면에서 1px 정도 두께로 고정
                float outline = 1.0f / s;

                for (var p2 : mc.level.players()) {
                    if (p2 == mc.player) continue;

                    String t = ClientTeamCache.getTeam(p2.getUUID());
                    if (t == null || !t.equals(myTeam)) continue;

                    int ox = Mth.floor(p2.getX());
                    int oz = Mth.floor(p2.getZ());
                    float oLocalX = (float) (ox + HALF - u);
                    float oLocalZ = (float) (oz + HALF - v);

                    if (oLocalX < -4 || oLocalZ < -4 || oLocalX > VIEW_PX + 4 || oLocalZ > VIEW_PX + 4) continue;

                    int color = ClientTeamCache.getTeamColor(t, 0xFF33FF66);
                    ClientPlayerIconRenderer.drawOutlinedDot(gg, oLocalX, oLocalZ, 2.0f, outline, color, 0xFF000000);

// 팀원 닉네임(작게, 가독성 배경)
String name = p2.getGameProfile().getName();
float nameScale = 0.6f;

gg.pose().pushPose();
gg.pose().translate(oLocalX, oLocalZ + 2.0f + 2.0f, 0);
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

        // ─────────────────────────────────────────────────────────
        // ✅ 플레이어 마커는 "항상 마지막"에 그려야 안 사라짐
        // (현재 pose는 미니맵 로컬 좌표계)
        // ─────────────────────────────────────────────────────────
        ClientPlayerIconRenderer.drawPlayerMarker(
                gg,
                playerLocalX,
                playerLocalZ,
                viewEntity.getYRot(),
                5.2f,          // ✅ 아이콘 크기(잘 보이게)
                0xFFFFD400,    // 노란 원
                0xCCFFFFFF,    // 삼각형
                0xFFFFFFFF     // 흰 점
        );

        gg.pose().popPose();
        gg.disableScissor();

        // 좌표 텍스트는 미니맵 아래 (요구대로 그대로)
        int ix = Mth.floor(viewEntity.getX());
        int iz = Mth.floor(viewEntity.getZ());
        String posText = "X: " + ix + "  Z: " + iz;

        int textW = mc.font.width(posText);
        int tx = x0 + (MINI_SIZE - textW) / 2;
        int ty = y0 + MINI_SIZE + 4;
        gg.drawString(mc.font, posText, tx, ty, 0xFFFFFFFF, true);
    }

    private static float maxDistToViewCorners(float cx, float cz, float view) {
        float x0 = 0, z0 = 0;
        float x1 = view, z1 = 0;
        float x2 = view, z2 = view;
        float x3 = 0, z3 = view;

        float d0 = dist(cx, cz, x0, z0);
        float d1 = dist(cx, cz, x1, z1);
        float d2 = dist(cx, cz, x2, z2);
        float d3 = dist(cx, cz, x3, z3);

        return Math.max(Math.max(d0, d1), Math.max(d2, d3));
    }

    private static float dist(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
}
