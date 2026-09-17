package com.ys.ys_mcbg;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HudOverlayHandler {

    private static String fmt(long ticks) {
        long sec = ticks / 20L;
        long m = sec / 60L;
        long s = sec % 60L;
        return m + "m " + s + "s";
    }

    // ==========================================================
    // ✅ 조준점 숨김: 힐템 사용 중이면 CROSSHAIR 오버레이 취소
    // ==========================================================

@SubscribeEvent
    public void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        boolean hide = ClientReviveProgressCache.isActive();
        if (!hide && player.isUsingItem() && (player.getUseItem().getItem() instanceof BgConsumableItem)) {
            hide = true;
        }

        if (hide) event.setCanceled(true);
    }

    // ==========================================================

    // ✅ HUD는 "가장 마지막"에 그려서 미니맵/자기장보다 위에 오게
    // ==========================================================
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gg = event.getGuiGraphics();
        Font font = mc.font;

        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        // 팀 정보 HUD는 생존/관전 모두 표시한다.
        // 기존 HUD는 오른쪽 위에 배치되어 있으므로 관전 중에도 그대로 유지한다.
        renderTeamHud(gg, mc, width, height);

        // 관전 중에는 팀 HUD만 유지하고 나머지 전투용 HUD는 그리지 않는다.
        if (player.isSpectator()) return;

        var s = ClientZoneCache.sample();
        if (!s.running) return;

        int centerX = width / 2;
        int centerY = height / 2;

        // =========================
        // ✅ PUBG 스타일 나침반(방위/도 + 팀원 점/거리)
        // =========================
        renderCompass(gg, mc, width);

        // --- HP bar ---
        int barWidth = 182;
        int barHeight = 10;
        int barX = centerX - (barWidth / 2);
        int barY = height - 15;

        // --- BOOST bar ---
        int boostH = 6;
        int boostY = barY - 10;

        // =========================
        // 킬/생존 (2줄) : HP바 왼쪽
        // =========================
        int killCount = ClientKillCache.killCount;
        int aliveCount = countPlayersInGameType(mc, GameType.ADVENTURE);

        String killText  = String.format("§l§c킬: %d§r", killCount);
        String aliveText = String.format("§l§a생존: %d§r", aliveCount);

        int maxW = Math.max(font.width(killText), font.width(aliveText));
        int leftX = Math.max(2, barX - 6 - maxW);

        int killY  = boostY - 1;
        int aliveY = barY + 1;

        gg.drawString(font, killText, leftX, killY, 0xFFFFFF, true);
        gg.drawString(font, aliveText, leftX, aliveY, 0xFFFFFF, true);

        // =========================
        // BOOST bar (노란색)
        // =========================
        gg.fill(barX - 1, boostY - 1, barX + barWidth + 1, boostY + boostH + 1, 0x55000000);

        float boost = ClientBoostCache.boost;

        float onePixel = 1.0f / (float) barWidth;
        if (boost < onePixel) boost = 0f;

        int boostFilled = (int) (barWidth * Mth.clamp(boost, 0f, 1f));
        if (boostFilled > 0) {
            gg.fill(barX, boostY, barX + boostFilled, boostY + boostH, 0x55FFD400);
        }

        // =========================
        // HP bar
        // =========================
        gg.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x55000000);

        float health = player.getHealth() + player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth() + player.getAbsorptionAmount();
        maxHealth = Math.max(maxHealth, 1.0f);

        int filled = (int) (barWidth * Mth.clamp(health / maxHealth, 0, 1));
        if (filled > 0) gg.fill(barX, barY, barX + filled, barY + barHeight, 0x55FF0000);

        String hpText = String.format("§l%d / %d", (int) health, (int) maxHealth);
        gg.drawString(font, hpText, barX + 2, barY + 1, 0xFFFFFF, true);

        // ==========================================================
        // ✅ 중앙 사용 게이지(링)
        //   - 여기서는 절대 return 하지 말고 "조건부"로만 그리기
        // ==========================================================
        if (ClientReviveProgressCache.isActive()) {
            int remain = ClientReviveProgressCache.getRemainingTicks();
            int total  = Math.max(1, ClientReviveProgressCache.getTotalTicks());

            if (remain > 0 && remain <= total) {
                float sec = remain / 20.0f;
                String t = String.format("%.1f", sec);

                float progress = (remain / (float) total);
                progress = Mth.clamp(progress, 0f, 1f);

                int cx = centerX;
                int cy = centerY;

                int radius = 12;
                int thickness = 3;
                int segments = 96;

                int innerFillRadius = Math.max(0, radius - thickness - 1);
                fillSmoothCircle(gg, cx, cy, innerFillRadius, segments, 0x66000000);

                drawSmoothRing(gg, cx, cy, radius, thickness, 1.0f, segments, 0x55000000);
                drawSmoothRing(gg, cx, cy, radius, thickness, progress, segments, 0xDDEFEFEF);

                int tw = font.width(t);
                gg.drawString(font, t, cx - tw / 2, cy - 4, 0xFFFFFFFF, true);

                String itemName = "소생중..";
                int nameY = cy + radius + 6;
                gg.drawString(font, itemName, cx - font.width(itemName) / 2, nameY, 0xFFFFFF, true);
            }
        } else if (player.isUsingItem() && (player.getUseItem().getItem() instanceof BgConsumableItem)) {

            int remain = player.getUseItemRemainingTicks();
            int total  = Math.max(1, player.getUseItem().getUseDuration());

            if (remain > 1 && remain < total - 1) {
                float sec = remain / 20.0f;
                String t = String.format("%.1f", sec);

                float progress = (remain / (float) total);
                progress = Mth.clamp(progress, 0f, 1f);

                int cx = centerX;
                int cy = centerY;

                int radius = 12;
                int thickness = 3;
                int segments = 96;

                int innerFillRadius = Math.max(0, radius - thickness - 1);
                fillSmoothCircle(gg, cx, cy, innerFillRadius, segments, 0x66000000);

                drawSmoothRing(gg, cx, cy, radius, thickness, 1.0f, segments, 0x55000000);
                drawSmoothRing(gg, cx, cy, radius, thickness, progress, segments, 0xDDEFEFEF);

                int tw = font.width(t);
                gg.drawString(font, t, cx - tw / 2, cy - 4, 0xFFFFFFFF, true);

                String itemName = player.getUseItem().getHoverName().getString();
                int nameY = cy + radius + 6;
                gg.drawString(font, itemName, cx - font.width(itemName) / 2, nameY, 0xFFFFFF, true);
            }
        }

        // ==========================================================
        // ✅ (핵심) 미니맵/자기장보다 무조건 위에:
        //   - 맨 마지막에 그리기
        //   - Z를 크게 올려서(1000) 렌더 우선순위 보장
        // ==========================================================
        int x = 6;
        int y = 6;

        boolean waiting = (s.shrinkLeftTicks > 0);
        String label = waiting ? "대기중.." : "축소중..";
        String timeText = waiting ? fmt(s.shrinkLeftTicks) : fmt(s.nextZoneTicks);

        int timeColor = waiting ? 0xFFFFFF : 0xFF5555;

        gg.pose().pushPose();
        gg.pose().translate(0, 0, 1000);

        // “확실한” 그림자(더 진하게)
        drawShadowText(gg, font, label, x + 5, y + 5, 0xFFFFFF);
        drawShadowText(gg, font, timeText, x + 5, y + 15, timeColor);

        gg.pose().popPose();
    }

    // ==========================================================
    // CUSTOM TEAM HUD
    // - 왼쪽 아래에 표시
    // - 커스텀 팀 시스템(ClientTeamCache)만 사용
    // - 이름은 위, HP 바는 이름 아래에 길게 표시
    // - 숫자 HP는 표시하지 않음
    // - 관전자(SPECTATOR)는 사망으로 표시
    // ==========================================================
    private static final int TEAM_HUD_MAX_MEMBERS = 4;
    private static final int TEAM_HUD_ROW_H = 26;
    private static final int TEAM_HUD_WIDTH = 140;
    private static final int TEAM_HUD_PADDING_X = 6;
    private static final int TEAM_HUD_BAR_W = 124;
    private static final int TEAM_HUD_BAR_H = 5;

    private static void renderTeamHud(GuiGraphics gg, Minecraft mc, int screenW, int screenH) {
        if (mc.player == null || mc.level == null) return;

        String myTeam = ClientTeamCache.getTeam(mc.player.getUUID());
        if (myTeam == null || myTeam.isBlank()) return;

        ClientPacketListener conn = mc.getConnection();
        List<? extends Player> entities = mc.level.players();
        List<TeamHudMember> members = new ArrayList<>();

        // 온라인 플레이어 목록을 기준으로 커스텀 팀원을 수집한다.
        // 이렇게 해야 관전자 상태의 팀원도 HUD에서 사망 처리할 수 있다.
        if (conn != null) {
            for (PlayerInfo info : conn.getOnlinePlayers()) {
                if (info == null || info.getProfile() == null) continue;

                var profile = info.getProfile();
                String team = ClientTeamCache.getTeam(profile.getId());
                if (team == null || !team.equals(myTeam)) continue;

                Player entity = null;
                for (Player p : entities) {
                    if (p != null && p.getUUID().equals(profile.getId())) {
                        entity = p;
                        break;
                    }
                }

                boolean spectator = info.getGameMode() == GameType.SPECTATOR;
                members.add(new TeamHudMember(
                        profile.getId(),
                        profile.getName(),
                        entity,
                        spectator
                ));
            }
        } else {
            // 연결 정보가 아직 없는 아주 초기 프레임에서는 엔티티 목록으로 fallback.
            for (Player p : entities) {
                if (p == null) continue;
                String team = ClientTeamCache.getTeam(p.getUUID());
                if (team == null || !team.equals(myTeam)) continue;
                members.add(new TeamHudMember(
                        p.getUUID(),
                        p.getGameProfile().getName(),
                        p,
                        false
                ));
            }
        }

        if (members.isEmpty()) return;

        // 자기 자신을 먼저, 나머지는 이름순.
        members.sort(Comparator
                .comparing((TeamHudMember m) -> !m.uuid.equals(mc.player.getUUID()))
                .thenComparing(m -> m.name, String.CASE_INSENSITIVE_ORDER));

        int count = Math.min(TEAM_HUD_MAX_MEMBERS, members.size());

        int x = Math.max(8, screenW - TEAM_HUD_WIDTH - 8);
        int y = 8;

        gg.pose().pushPose();
        gg.pose().translate(0, 0, 1000);

        for (int i = 0; i < count; i++) {
            renderTeamHudRow(gg, mc, x, y + i * TEAM_HUD_ROW_H, members.get(i), myTeam);
        }

        gg.pose().popPose();
    }

    private static void renderTeamHudRow(GuiGraphics gg, Minecraft mc, int x, int y,
                                         TeamHudMember member, String teamName) {
        final Font font = mc.font;
        final int rowH = TEAM_HUD_ROW_H;
        final int panelW = TEAM_HUD_WIDTH;

        // 반투명 검정 배경
        gg.fill(x, y, x + panelW, y + rowH - 1, 0x70000000);

        // 팀 색상 세로 포인트
        int teamColor = ClientTeamCache.getTeamColor(teamName, 0xFF33FF66);
        gg.fill(x, y, x + 3, y + rowH - 1, teamColor);

        boolean self = member.uuid.equals(mc.player.getUUID());
        boolean downed = member.entity != null && ClientDownedCache.isDowned(member.uuid);
        boolean dead = member.spectator || (member.entity != null && member.entity.isDeadOrDying());

        // 이름은 바 위에 별도로 표시하여 긴 이름도 HP 바와 겹치지 않게 한다.
        String name = member.name == null ? "Unknown" : member.name;
        float nameScale = 0.8f;
        int availableNameWidth = panelW - 12;
        if (font.width(name) > availableNameWidth) {
            nameScale = Math.max(0.58f, availableNameWidth / (float) font.width(name));
        }

        int nameColor;
        if (dead) {
            nameColor = 0xFFAAAAAA;
        } else if (downed) {
            nameColor = 0xFFFF5555;
        } else if (self) {
            nameColor = 0xFFFFFFAA;
        } else {
            nameColor = 0xFFFFFFFF;
        }

        gg.pose().pushPose();
        gg.pose().translate(x + 6, y + 1, 0);
        gg.pose().scale(nameScale, nameScale, 1.0f);
        drawShadowText(gg, font, name, 0, 0, nameColor);
        gg.pose().popPose();

        // 이름 바로 아래에 긴 HP 바.
        int barX = x + 6;
        int barY = y + 15;
        int barW = Math.min(TEAM_HUD_BAR_W, panelW - 12);
        int barH = TEAM_HUD_BAR_H;

        // 바 배경
        gg.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0x66000000);
        gg.fill(barX, barY, barX + barW, barY + barH, 0x55333333);

        if (dead) {
            // 관전자 = 사망. 빈 HP 바 + 사망 표시.
            gg.fill(barX, barY, barX + barW, barY + barH, 0xFF555555);
            String deadText = "사망";
            gg.drawString(font, deadText,
                    barX + barW - font.width(deadText) - 3,
                    barY - 1,
                    0xFFDDDDDD,
                    true);
            return;
        }

        if (member.entity == null) {
            // 플레이어 엔티티가 보이지 않으면 HP를 알 수 없으므로 회색으로 표시한다.
            gg.fill(barX, barY, barX + barW, barY + barH, 0xFF666666);
            return;
        }

        float maxHealth = Math.max(1.0f, member.entity.getMaxHealth());
        float health = Mth.clamp(member.entity.getHealth(), 0.0f, maxHealth);
        float ratio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);

        int hpColor;
        if (ratio <= 0.25f) {
            hpColor = 0xFFE83F3F;
        } else if (ratio <= 0.50f) {
            hpColor = 0xFFFFB52E;
        } else {
            hpColor = 0xFF4DDB6B;
        }

        int filled = Math.round(barW * ratio);
        if (filled > 0) {
            gg.fill(barX, barY, barX + filled, barY + barH, hpColor);
        }
    }

    private static final class TeamHudMember {
        private final java.util.UUID uuid;
        private final String name;
        private final Player entity;
        private final boolean spectator;

        private TeamHudMember(java.util.UUID uuid, String name, Player entity, boolean spectator) {
            this.uuid = uuid;
            this.name = name;
            this.entity = entity;
            this.spectator = spectator;
        }
    }

    private static void drawShadowText(GuiGraphics gg, Font font, String text, int x, int y, int color) {
        gg.drawString(font, text, x + 1, y + 1, 0xAA000000, false);
        gg.drawString(font, text, x, y, color, false);
    }

    private static int countPlayersInGameType(Minecraft mc, GameType type) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return 0;

        int count = 0;
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            if (info != null && info.getGameMode() == type) count++;
        }
        return count;
    }

    // =========================
    // Smooth Ring
    // =========================
    private static void drawSmoothRing(GuiGraphics graphics, int cx, int cy, int radius, int thickness,
                                       float progress, int segments, int argb) {
        if (progress <= 0f) return;

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        float outer = radius;
        float inner = Math.max(0f, radius - thickness);

        float start = (float) (-Math.PI / 2.0);
        float end   = start + (float) (Math.PI * 2.0) * progress;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack pose = graphics.pose();
        Matrix4f mat = pose.last().pose();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float tt = i / (float) segments;
            float ang = Mth.lerp(tt, start, end);

            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);

            float xOuter = cx + cos * outer;
            float yOuter = cy + sin * outer;
            float xInner = cx + cos * inner;
            float yInner = cy + sin * inner;

            buf.vertex(mat, xOuter, yOuter, 0).color(r, g, b, a).endVertex();
            buf.vertex(mat, xInner, yInner, 0).color(r, g, b, a).endVertex();
        }

        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // =========================
    // Smooth Circle Fill
    // =========================
    private static void fillSmoothCircle(GuiGraphics graphics, int cx, int cy, int radius, int segments, int argb) {
        if (radius <= 0) return;

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack pose = graphics.pose();
        Matrix4f mat = pose.last().pose();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        buf.vertex(mat, cx, cy, 0).color(r, g, b, a).endVertex();

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float ang = (float) (t * Math.PI * 2.0);

            float x = cx + (float) Math.cos(ang) * radius;
            float y = cy + (float) Math.sin(ang) * radius;

            buf.vertex(mat, x, y, 0).color(r, g, b, a).endVertex();
        }

        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

// ==========================================================
// ✅ PUBG 스타일 나침반(방위/도 + 팀원 점/거리)
// ==========================================================
private static float wrap360(float deg) {
    deg = deg % 360.0f;
    if (deg < 0) deg += 360.0f;
    return deg;
}

// 나침반 숫자/틱이 '툭툭' 바뀌지 않도록 헤딩을 부드럽게 보정
private static float SMOOTH_HEADING = Float.NaN;

private static float smoothHeading360(float rawHeading) {
    rawHeading = wrap360(rawHeading);
    if (Float.isNaN(SMOOTH_HEADING)) {
        SMOOTH_HEADING = rawHeading;
        return rawHeading;
    }
    // rotLerp는 -180..180 기준 보간이므로, 0..360으로 다시 감싼다.
    float lerped = Mth.rotLerp(0.25f, SMOOTH_HEADING, rawHeading);
    SMOOTH_HEADING = wrap360(lerped);
    return SMOOTH_HEADING;
}

private static String dirLabel(int deg) {
    int d = ((deg % 360) + 360) % 360;
    return switch (d) {
        case 0 -> "N";
        case 45 -> "NE";
        case 90 -> "E";
        case 135 -> "SE";
        case 180 -> "S";
        case 225 -> "SW";
        case 270 -> "W";
        case 315 -> "NW";
        default -> null;
    };
}

private static void renderCompass(GuiGraphics gg, Minecraft mc, int screenW) {
    if (mc.player == null || mc.level == null) return;

    // Minecraft yaw(0=남, 180=북) -> 나침반 기준(0=북, 90=동)
    float rawHeading = wrap360(mc.player.getYRot() + 180.0f);
    float heading = smoothHeading360(rawHeading);

    int centerX = screenW / 2;

    int barW = Math.min(360, screenW - 20);
    int barH = 26;
    int barX = centerX - (barW / 2);
    int barY = 4;

    float halfSpan = 90.0f; // 화면에 보여주는 각도 범위: 좌/우 90도
    // 숫자 간격을 "조금" 더 좁히기: px/deg를 살짝 줄여 압축 렌더
    float pxPerDeg = (float) barW / (halfSpan * 2.0f) * 0.92f;

    // 중심 표시(현재 바라보는 방향)
    gg.fill(centerX, barY + 1, centerX + 1, barY + barH - 1, 0xCCFFFFFF);

    // 눈금/라벨: 10도 단위(요청), 숫자는 30도마다, 방위(N/NE/...)는 45도에서 표시
    final int tickStep = 10;
    final int numberStep = 10;

    // 현재 시야 범위(heading ± halfSpan) 안에 들어오는 '세계 각도' 틱들을 뽑아 x를 계산해 그린다.
    float start = heading - halfSpan;
    float end = heading + halfSpan;

    int startTick = (int) Math.floor(start / tickStep) * tickStep;
    int endTick = (int) Math.ceil(end / tickStep) * tickStep;

    for (int tick = startTick; tick <= endTick; tick += tickStep) {
        float tickWorld = wrap360(tick);
        float rel = Mth.wrapDegrees(tickWorld - heading); // [-180..180]
        if (rel < -halfSpan - 0.001f || rel > halfSpan + 0.001f) continue;

        float xf = centerX + rel * pxPerDeg;
        int x = Math.round(xf);
        if (x < barX + 1 || x >= barX + barW - 1) continue;

        boolean major = (Math.floorMod(tick, numberStep) == 0);
        int tickH = major ? 8 : 4;

        int yTop = barY + 2;
        gg.fill(x, yTop, x + 1, yTop + tickH, 0xCCFFFFFF);

        if (major) {
            int ang = ((Math.round(tickWorld) % 360) + 360) % 360;

            // 숫자 라벨(30도)
            String lbl = String.valueOf(ang);
            float scale = 0.62f;
            gg.pose().pushPose();
            // 숫자는 아래쪽으로 내려서 방위 글자와 겹침 방지
            gg.pose().translate(xf, barY + 15, 0);
            gg.pose().scale(scale, scale, 1.0f);

            int tw = mc.font.width(lbl);
            gg.drawString(mc.font, lbl, -tw / 2, 0, 0xFFFFFFFF, true);
            gg.pose().popPose();
        }
    }

    // 방위(N/NE/E...)는 45도 지점에서 따로 렌더(10도 틱과 정렬이 안 맞을 수 있어서)
    for (int d = 0; d < 360; d += 45) {
        float rel = Mth.wrapDegrees(d - heading);
        if (rel < -halfSpan - 0.001f || rel > halfSpan + 0.001f) continue;
        float xf = centerX + rel * pxPerDeg;
        int x = Math.round(xf);
        if (x < barX + 1 || x >= barX + barW - 1) continue;

        String lbl = dirLabel(d);
        if (lbl == null) continue;

        // 방향 글자는 조금 더 큼
        float scale = 0.78f;
        gg.pose().pushPose();
        // 방위 글자는 위쪽
        gg.pose().translate(xf, barY + 7, 0);
        gg.pose().scale(scale, scale, 1.0f);
        int tw = mc.font.width(lbl);
        gg.drawString(mc.font, lbl, -tw / 2, 0, 0xFFFFFFFF, true);
        gg.pose().popPose();
    }

    // ─────────────────────────────────────────────
    // 팀원 점 + 거리(m)
    // ─────────────────────────────────────────────
    String myTeam = ClientTeamCache.getTeam(mc.player.getUUID());
    if (myTeam == null) return;

    int dotY = barY + 15;

    for (var p2 : mc.level.players()) {
        if (p2 == mc.player) continue;

        String t = ClientTeamCache.getTeam(p2.getUUID());
        if (t == null || !t.equals(myTeam)) continue;

        double dx = p2.getX() - mc.player.getX();
        double dz = p2.getZ() - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

        // bearing: 0=북, 90=동
        float bearing = wrap360((float) Math.toDegrees(Math.atan2(dx, -dz)));

        float rel = Mth.wrapDegrees(bearing - heading); // [-180..180]
        float clamped = Mth.clamp(rel, -halfSpan, halfSpan);

        int x = (int) (centerX + clamped * pxPerDeg);
        x = Mth.clamp(x, barX + 6, barX + barW - 6);

        int color = ClientTeamCache.getTeamColor(t, 0xFF33FF66);

        // 점(테두리 포함)
        ClientPlayerIconRenderer.drawOutlinedDot(gg, x, dotY, 2.4f, 1.1f, color, 0xFF000000);

        // 거리 텍스트(작게)
        String d = dist + "m";
        float s = 0.55f;

        gg.pose().pushPose();
        gg.pose().translate(x, dotY + 3, 0);
        gg.pose().scale(s, s, 1.0f);

        int tw = mc.font.width(d);
        int tx = -tw / 2;
        int ty = 0;

        gg.fill(tx - 2, ty - 1, tx + tw + 2, ty + mc.font.lineHeight, 0xAA000000);
        gg.drawString(mc.font, d, tx, ty, 0xFFFFFFFF, true);

        gg.pose().popPose();
    }
}


}
