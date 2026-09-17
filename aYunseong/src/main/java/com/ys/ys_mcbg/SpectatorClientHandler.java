package com.ys.ys_mcbg;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerChangeGameTypeEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** 클라이언트 관전 입력/카메라 잠금/UI 제한 담당. */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpectatorClientHandler {
    private static boolean spectator = false;
    /** 서버가 마지막으로 지정한 카메라 대상. null = 커스텀 잠금 없음(1인 서버 허용). */
    private static UUID lockedTargetId = null;
    private static int lastHotbarSlot = -1;

    private SpectatorClientHandler() {}

    public static void setLockedTarget(UUID targetId) {
        lockedTargetId = targetId;
    }

    @SubscribeEvent
    public static void onGameModeChanged(ClientPlayerChangeGameTypeEvent event) {
        spectator = event.getNewGameType() == GameType.SPECTATOR;
        if (!spectator) {
            lockedTargetId = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide()) return;
        spectator = false;
        lockedTargetId = null;
        lastHotbarSlot = -1;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        spectator = false;
        lockedTargetId = null;
        lastHotbarSlot = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            spectator = false;
            lockedTargetId = null;
            lastHotbarSlot = -1;
            return;
        }

        spectator = player.isSpectator();
        if (!spectator) {
            lockedTargetId = null;
            if (mc.getCameraEntity() != player) mc.setCameraEntity(player);
            return;
        }

        // spectator hotbar 숫자 입력이 vanilla spectator UI와 충돌하지 않도록
        // 현재 슬롯 값을 고정한다. 실제 관전 번호 선택은 InputEvent.Key에서 서버로 보낸다.
        if (lastHotbarSlot < 0) lastHotbarSlot = player.getInventory().selected;
        else player.getInventory().selected = lastHotbarSlot;

        // 서버가 지정한 대상이 있으면 vanilla spectator의 자유 카메라/대상 전환보다
        // 서버 잠금을 우선한다.
        if (lockedTargetId != null && mc.level != null) {
            Entity target = mc.level.getPlayerByUUID(lockedTargetId);
            if (target instanceof Player && target != player && mc.getCameraEntity() != target) {
                mc.setCameraEntity(target);
            }
        }
    }

    /**
     * 1~9는 vanilla hotbar 동작 대신 커스텀 관전 대상 번호로 사용한다.
     * 채팅/ESC 등 GUI가 열려 있을 때는 입력을 건드리지 않는다.
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isSpectator()) return;
        if (mc.screen != null) return;
        if (event.getAction() != InputConstants.PRESS) return;

        if (event.getKey() >= GLFW.GLFW_KEY_1 && event.getKey() <= GLFW.GLFW_KEY_9) {
            int index = event.getKey() - GLFW.GLFW_KEY_1;
            if (mc.getConnection() != null) {
                ysMcbgMod.CHANNEL.sendToServer(new SpectatorIndexPacket(index));
            }
        }
    }

    /** 일반 플레이용 HUD만 차단한다. 채팅/ESC/타이틀/서브타이틀/액션바는 건드리지 않는다. */
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isSpectator()) return;

        boolean hide = event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.AIR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.MOUNT_HEALTH.type()
                || event.getOverlay() == VanillaGuiOverlay.JUMP_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.ITEM_NAME.type()
                || event.getOverlay() == VanillaGuiOverlay.POTION_ICONS.type()
                || event.getOverlay() == VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()
                || event.getOverlay() == VanillaGuiOverlay.VIGNETTE.type()
                || event.getOverlay() == VanillaGuiOverlay.HELMET.type()
                || event.getOverlay() == VanillaGuiOverlay.PORTAL.type()
                || event.getOverlay() == VanillaGuiOverlay.SPYGLASS.type()
                || event.getOverlay() == VanillaGuiOverlay.SCOREBOARD.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_LIST.type();

        if (hide) event.setCanceled(true);
    }
}
