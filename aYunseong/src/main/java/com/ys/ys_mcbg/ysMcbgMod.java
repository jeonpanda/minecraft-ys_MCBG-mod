package com.ys.ys_mcbg;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

import org.slf4j.Logger;

@Mod(ysMcbgMod.MODID)
public class ysMcbgMod {
    public static final String MODID = "ys_mcbg";
    private static final String PROTOCOL_VERSION = "1";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** [MCBG] 관리자용 메시지를 받을 수 있는 플레이어인지 확인한다. */
    public static boolean isAdminPlayer(ServerPlayer player) {
        return player != null && player.getTags().contains("admin");
    }

    /** [MCBG] 기존 전파 범위를 유지하되 admin 태그가 있는 플레이어에게만 표시한다. */
    private static void sendAdminSuccess(CommandSourceStack source, Component message, boolean broadcastToAdmins) {
        if (source == null || message == null) return;

        if (broadcastToAdmins) {
            MinecraftServer server = source.getServer();
            if (server == null) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(player)) {
                    player.sendSystemMessage(message);
                }
            }
            return;
        }

        if (source.getEntity() instanceof ServerPlayer player && isAdminPlayer(player)) {
            source.sendSuccess(() -> message, false);
        }
    }

    /** [MCBG] 오류 메시지도 admin 태그가 있는 실행자에게만 표시한다. */
    private static void sendAdminFailure(CommandSourceStack source, Component message) {
        if (source != null && source.getEntity() instanceof ServerPlayer player && isAdminPlayer(player)) {
            source.sendFailure(message);
        }
    }

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean packetsRegistered = false;



    public ysMcbgMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        // ✅ 맵 빌드/전송(서버)
        MinecraftForge.EVENT_BUS.register(new BgMapBuildManager());


        // 패킷 등록 (중복 방지)
        if (!packetsRegistered) {
            CHANNEL.messageBuilder(DownStatePacket.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(DownStatePacket::encode)
                    .decoder(DownStatePacket::decode)
                    .consumerMainThread(DownStatePacket::handle)
                    .add();

            CHANNEL.messageBuilder(ReviveTickPacket.class, 6, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(ReviveTickPacket::encode)
                    .decoder(ReviveTickPacket::decode)
                    .consumerMainThread(ReviveTickPacket::handle)
                    .add();

            CHANNEL.messageBuilder(ReviveProgressPacket.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(ReviveProgressPacket::encode)
                    .decoder(ReviveProgressPacket::decode)
                    .consumerMainThread(ReviveProgressPacket::handle)
                    .add();

            packetsRegistered = true;
            CHANNEL.registerMessage(
                    2,
                    BoostSyncPacket.class,
                    BoostSyncPacket::toBytes,
                    BoostSyncPacket::new,
                    BoostSyncPacket::handle
            );

            CHANNEL.messageBuilder(ZoneScheduleSyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(ZoneScheduleSyncPacket::encode)
                    .decoder(ZoneScheduleSyncPacket::decode)
                    .consumerMainThread(ZoneScheduleSyncPacket::handle)
                    .add();

            CHANNEL.registerMessage(
                    0,
                    PickupPacket.class,
                    PickupPacket::toBytes,
                    PickupPacket::new,
                    PickupPacket::handle
            );

            // ✅ 킬 동기화 패킷(서버 -> 클라 HUD)
            CHANNEL.registerMessage(
                    1,
                    KillSyncPacket.class,
                    KillSyncPacket::toBytes,
                    KillSyncPacket::new,
                    KillSyncPacket::handle
            );

            CHANNEL.registerMessage(4, MapDataPartPacket.class,
                    MapDataPartPacket::encode,
                    MapDataPartPacket::decode,
                    MapDataPartPacket::handle
            );

            // ✅ 팀 동기화(서버 -> 클라). 등록 누락 시 로그인/명령어 동기화에서 인게임 오류(패킷 미등록) 발생.
            CHANNEL.messageBuilder(TeamSyncPacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(TeamSyncPacket::encode)
                    .decoder(TeamSyncPacket::decode)
                    .consumerMainThread(TeamSyncPacket::handle)
                    .add();

            // 팀원 600블록 장거리 마커 동기화 (서버 -> 클라이언트)
            CHANNEL.messageBuilder(TeamMarkerSyncPacket.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(TeamMarkerSyncPacket::encode)
                    .decoder(TeamMarkerSyncPacket::decode)
                    .consumerMainThread(TeamMarkerSyncPacket::handle)
                    .add();

            // ✅ 커스텀 관전 대상 변경 요청(클라이언트 -> 서버)
            CHANNEL.messageBuilder(SpectatorTargetPacket.class, 10, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(SpectatorTargetPacket::encode)
                    .decoder(SpectatorTargetPacket::decode)
                    .consumerMainThread(SpectatorTargetPacket::handle)
                    .add();

            // 관전 번호 선택(클라이언트 -> 서버). 대상 목록은 서버에서 직접 계산하여 검증한다.
            CHANNEL.messageBuilder(SpectatorIndexPacket.class, 11, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(SpectatorIndexPacket::encode)
                    .decoder(SpectatorIndexPacket::new)
                    .consumerMainThread(SpectatorIndexPacket::handle)
                    .add();

            // 서버가 vanilla spectator 카메라보다 우선할 대상을 클라이언트에 전달
            CHANNEL.messageBuilder(SpectatorCameraLockPacket.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(SpectatorCameraLockPacket::encode)
                    .decoder(SpectatorCameraLockPacket::decode)
                    .consumerMainThread(SpectatorCameraLockPacket::handle)
                    .add();

            CHANNEL.messageBuilder(AutoInventorySortPacket.class, 13, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(AutoInventorySortPacket::toBytes)
                    .decoder(AutoInventorySortPacket::new)
                    .consumerMainThread(AutoInventorySortPacket::handle)
                    .add();

        }

        // 공용 이벤트(서버/클라) 등록
        MinecraftForge.EVENT_BUS.register(this);

        // 클라이언트 전용 등록은 ClientModEvents에서만
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientModEvents::init);
    }

    // =======================
    // 왼손 사용 차단
    // =======================

    private void cancelOffHandInteraction(PlayerInteractEvent event) {
        if (event.getHand() == InteractionHand.OFF_HAND) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelOffHandInteraction(event);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelOffHandInteraction(event);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelOffHandInteraction(event);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelOffHandInteraction(event);
    }

    // =======================
    // 아이템 습득/소멸 관련
    // =======================

    // 아이템 습득 지연 + 수명 무한
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item) {
            item.setPickUpDelay(32767);
            item.lifespan = Integer.MAX_VALUE;
        }
    }

    // 서서 아이템 줍는 것 방지
    @SubscribeEvent
    public void onPlayerPickup(EntityItemPickupEvent event) {
        event.setCanceled(true);
    }

    // 아이템 자연 소멸 방지
    @SubscribeEvent
    public void onItemExpire(ItemExpireEvent event) {
        event.setCanceled(true);
    }

    // 서버 전용: 아이템 획득 성공 시 소리
    public static void playPickupSound(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                1.0F, 1.0F
        );
    }

    // =======================
    // ✅ Revive progress HUD (server -> client)
    // =======================

    public static void sendReviveProgress(ServerPlayer p, boolean active, int remainingTicks, int totalTicks) {
        if (p == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ReviveProgressPacket(active, remainingTicks, totalTicks));
    }


    // =======================
    // ✅ 킬 감지/저장/동기화
    // =======================

    /**
     * 플레이어가 "플레이어"를 죽였을 때만 카운트
     * - TACZ 같은 총 모드도 대부분 LivingDeathEvent + DamageSource로 잡힘
     */
    @SubscribeEvent
    public void onPlayerKilled(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        // ✅ 다운 처리(즉사 방지) 먼저
        if (!BgPlayerState.isDowned(victim)
                && !victim.isCreative()
                && !victim.isSpectator()
                && shouldDownInsteadOfDie(victim)) {

            // 다운 가해자(기절시킨 사람) 기록
            ServerPlayer downer = resolveKiller(event); // null 가능

            event.setCanceled(true);
            beginDowned(victim, downer);
            return; // 다운이면 "사망" 처리/킬 집계 안 함
        }

        if (event.isCanceled()) return;

            // ✅ 최종 사망(출혈사/마무리 처치 등)
    java.util.UUID downedById = BgPlayerState.getDownedBy(victim);

    // FINISHER(마지막 타격자)
    ServerPlayer finisher = resolveKiller(event);

    // KILL CREDIT(킬 스코어): 다운시킨 사람이 있으면 그 사람에게
    ServerPlayer creditedPlayer = finisher;
    if (downedById != null) {
        ServerPlayer downer = victim.getServer().getPlayerList().getPlayer(downedById);
        if (downer != null) creditedPlayer = downer;
    }

    // ✅ 킬피드(메시지)는 FINISHER 기준으로 출력
    broadcastKillFeedDeath(victim, finisher);

    // ✅ 팀킬이면 스코어 증가 금지 (모드 팀 시스템 기준, 바닐라 팀 사용 안 함)
    if (creditedPlayer != null && creditedPlayer != victim && !isTeamKill(creditedPlayer, victim)) {
        KillData data = KillData.get(victim.server);
        data.addKills(creditedPlayer.getUUID(), 1);
        syncKillsToClient(creditedPlayer);
    }

    // 다음 라운드/부활 대비: 기록 제거
    BgPlayerState.clearDownedBy(victim);
}

    // 접속/리스폰 시 HUD 초기 동기화
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        syncKillsToClient(sp);
        syncAllDownedToClient(sp);

        // 서버 재접속 등으로 다운 상태가 남아있는 경우 복원
        if (BgPlayerState.isDowned(sp)) {
            BgPlayerState.applyDownedMaxHealth(sp);
            sp.setPose(Pose.SWIMMING);
            sp.setForcedPose(Pose.SWIMMING);
            sp.setSwimming(true);
            sp.refreshDimensions();
            // 클라 캐시가 꼬였을 수 있으니 한 번 더 브로드캐스트
            broadcastDowned(sp.getServer(), sp.getUUID(), true);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // 사망 후 리스폰이면 다운 관련 태그/속성 정리
        clearDowned(sp, false);
        syncKillsToClient(sp);
        syncAllDownedToClient(sp);
    }

    // ✅ 데이터팩에서 호출 가능한 초기화 커맨드 제공
    // 예) data/ys_mcbg/functions/reset_kills.mcfunction 안에:
    //     mcbg resetkills @a
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mcbg")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("undown")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    int count = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                        if (BgPlayerState.isDowned(p)) {
                                            clearDowned(p, true); // 해제 후 체력 10 포함
                                            count++;
                                        }
                                    }
                                    int finalCount = count;
                                    sendAdminSuccess(ctx.getSource(), Component.literal("[MCBG] 기절 해제: " + finalCount + "명"), true);
                                    return count;
                                })
                        )
                )

                .then(Commands.literal("rebuildmap")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    Collection<ServerPlayer> players =
                                            EntityArgument.getPlayers(ctx, "targets");

                                    ServerLevel level = ctx.getSource().getLevel();

                                    // 맵 리빌드 1번 실행
                                    BgMapBuildManager.requestRebuild(level);

                                    // 모든 대상 플레이어에게 전송
                                    BgMapSavedData data = BgMapSavedData.get(level);

                                    for (ServerPlayer sp : players) {
                                        BgMapBuildManager.sendToPlayer(sp, data);
                                    }

                                    sendAdminSuccess(ctx.getSource(),
                                            Component.literal("[MCBG] 맵 리빌드 중... : " + players.size() + "명"), true);

                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("resetboost")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                    for (ServerPlayer p : targets) {
                                        p.getPersistentData().putFloat("mcbg_boost", 0f);
                                        ysMcbgMod.CHANNEL.send(
                                                PacketDistributor.PLAYER.with(() -> p),
                                                new BoostSyncPacket(0f)
                                        );
                                    }
                                    sendAdminSuccess(ctx.getSource(),
                                            Component.literal("[MCBG] 부스트 초기화 완료: " + targets.size() + "명"), true);
                                    return 1;
                                }
                                )
                        )
                )

                // ✅ Killboard reset
                .then(Commands.literal("resetkills")
                        .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(ctx -> {

                                    Collection<ServerPlayer> players =
                                            net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "targets");

                                    MinecraftServer server = ctx.getSource().getServer();
                                    ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                                    if (overworld != null) {
                                        KillData data = KillData.get(server); // ✅ KillData.get이 server 받는 버전이면 이걸로
                                        // KillData data = KillData.get(overworld); // ✅ 만약 level 받는 버전이면 이걸로

                                        for (ServerPlayer sp : players) {
                                            data.reset(sp.getUUID());          // ✅ private 접근 제거
                                            syncKillsToClient(sp);             // ✅ player 단위로 HUD 동기화
                                        }
                                    }

                                    sendAdminSuccess(ctx.getSource(), Component.literal("[MCBG] 킬 초기화 완료"), true);

                                    return 1;
                                })
                        )
                )


                // /mcbg resetteam
                .then(Commands.literal("resetteam")
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            BgTeamSavedData data = BgTeamSavedData.get(server.overworld());
                            data.resetAll();
                            BgTeamManager.syncToAll(server);
                            sendAdminSuccess(ctx.getSource(), Component.literal("[MCBG] 모든 팀/팀원 정보가 초기화되었습니다."), true);
                            return 1;
                        })
                )


                // ✅ PUBG-like moving worldborder
                .then(Commands.literal("worldborder")
                        .then(Commands.literal("start")
                                .executes(ctx -> startZone(ctx.getSource(), null))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> startZone(ctx.getSource(), LongArgumentType.getLong(ctx, "seed")))
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopZone(ctx.getSource()))
                        )
                        .then(Commands.literal("set")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setElapsed(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")))
                                )
                        )
                        // wait minutes per phase (1..5)
                        .then(Commands.literal("waits")
                                .then(Commands.argument("w1", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("w2", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("w3", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("w4", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("w5", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setWaits(ctx.getSource(), new int[]{
                                                                                IntegerArgumentType.getInteger(ctx, "w1"),
                                                                                IntegerArgumentType.getInteger(ctx, "w2"),
                                                                                IntegerArgumentType.getInteger(ctx, "w3"),
                                                                                IntegerArgumentType.getInteger(ctx, "w4"),
                                                                                IntegerArgumentType.getInteger(ctx, "w5")
                                                                        }))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        // shrink minutes per phase (1..5)
                        .then(Commands.literal("shrinks")
                                .then(Commands.argument("s1", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("s2", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("s3", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("s4", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("s5", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setShrinks(ctx.getSource(), new int[]{
                                                                                IntegerArgumentType.getInteger(ctx, "s1"),
                                                                                IntegerArgumentType.getInteger(ctx, "s2"),
                                                                                IntegerArgumentType.getInteger(ctx, "s3"),
                                                                                IntegerArgumentType.getInteger(ctx, "s4"),
                                                                                IntegerArgumentType.getInteger(ctx, "s5")
                                                                        }))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        // end radius per phase (1..5)
                        .then(Commands.literal("radii")
                                .then(Commands.argument("r1", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("r2", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("r3", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("r4", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("r5", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setRadii(ctx.getSource(), new int[]{
                                                                                IntegerArgumentType.getInteger(ctx, "r1"),
                                                                                IntegerArgumentType.getInteger(ctx, "r2"),
                                                                                IntegerArgumentType.getInteger(ctx, "r3"),
                                                                                IntegerArgumentType.getInteger(ctx, "r4"),
                                                                                IntegerArgumentType.getInteger(ctx, "r5")
                                                                        }))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("seed")
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> setSeed(ctx.getSource(), LongArgumentType.getLong(ctx, "seed")))
                                )
                        )
                        .then(Commands.literal("info")
                                .executes(ctx -> infoZone(ctx.getSource()))
                        )
                        // legacy alias: /mcbg worldborder phases (wait minutes)
                        .then(Commands.literal("phases")
                                .then(Commands.argument("p1", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("p2", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("p3", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("p4", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("p5", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setWaits(ctx.getSource(), new int[]{
                                                                                IntegerArgumentType.getInteger(ctx, "p1"),
                                                                                IntegerArgumentType.getInteger(ctx, "p2"),
                                                                                IntegerArgumentType.getInteger(ctx, "p3"),
                                                                                IntegerArgumentType.getInteger(ctx, "p4"),
                                                                                IntegerArgumentType.getInteger(ctx, "p5")
                                                                        }))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

// ================= TEAM =================
// 등록된 팀 목록을 자동 완성 목록으로 제공하는 SuggestionProvider
                com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SUGGEST_TEAMS = (ctx, builder) -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    ServerLevel level = server.getLevel(Level.OVERWORLD);
                    if (level != null) {
                        BgTeamSavedData td = BgTeamSavedData.get(level);
                        return SharedSuggestionProvider.suggest(td.getTeamColors().keySet(), builder);
                    }
                    return builder.buildFuture();
                };

        dispatcher.register(Commands.literal("mcbg")
                .then(Commands.literal("team")

                        .then(Commands.literal("color")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUGGEST_TEAMS) // 팀 이름 자동완성 추가
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    java.util.List<String> colors = new java.util.ArrayList<>();
                                                    for (ChatFormatting f : ChatFormatting.values()) {
                                                        if (f.isColor()) colors.add(f.getName());
                                                    }
                                                    return SharedSuggestionProvider.suggest(colors, builder);
                                                })
                                                .executes(ctx -> {
                                                    MinecraftServer server = ctx.getSource().getServer();
                                                    String team = StringArgumentType.getString(ctx, "team");
                                                    String colorName = StringArgumentType.getString(ctx, "color").toLowerCase(Locale.ROOT);

                                                    ChatFormatting fmt = null;
                                                    for (ChatFormatting f : ChatFormatting.values()) {
                                                        if (!f.isColor()) continue;
                                                        if (f.getName().equalsIgnoreCase(colorName)) {
                                                            fmt = f;
                                                            break;
                                                        }
                                                    }
                                                    if (fmt == null || fmt.getColor() == null) {
                                                        sendAdminFailure(ctx.getSource(), Component.literal("[MCBG] 올바른 색상 이름이 아닙니다: " + colorName));
                                                        return 0;
                                                    }
                                                    int argb = 0xFF000000 | (fmt.getColor() & 0xFFFFFF);

                                                    BgTeamSavedData data = BgTeamSavedData.get(server.overworld());
                                                    data.setTeamColor(team, argb);
                                                    BgTeamManager.syncToAll(server);

                                                    sendAdminSuccess(ctx.getSource(), Component.literal("[MCBG] 팀 색상이 변경되었습니다: " + team + " -> " + colorName), false);
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    try {
                                        MinecraftServer server = ctx.getSource().getServer();
                                        ServerLevel level = server.getLevel(Level.OVERWORLD);
                                        if (level == null) return 0;

                                        BgTeamSavedData td = BgTeamSavedData.get(level);

                                        java.util.List<String> teams = new java.util.ArrayList<>(td.getTeamColors().keySet());
                                        java.util.Collections.sort(teams);

                                        String myTeam = null;
                                        try {
                                            ServerPlayer me = ctx.getSource().getPlayer();
                                            if (me != null) myTeam = td.getPlayerTeam().get(me.getUUID());
                                        } catch (Exception ignored) {}

                                        if (teams.isEmpty()) {
                                            ctx.getSource().sendSystemMessage(Component.literal("팀이 없습니다."));
                                            return 1;
                                        }

                                        StringBuilder sb = new StringBuilder();
                                        sb.append("팀 목록 (").append(teams.size()).append("):\n");
                                        for (String t : teams) {
                                            int count = 0;
                                            for (var e : td.getPlayerTeam().entrySet()) {
                                                if (t.equals(e.getValue())) count++;
                                            }
                                            sb.append(" - ").append(t).append(" (").append(count).append(")");
                                            if (myTeam != null && myTeam.equals(t)) sb.append(" §e(내 팀)§r");
                                            sb.append("\n");
                                        }
                                        ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                                        return teams.size();

                                    } catch (Throwable t) {
                                        ysMcbgMod.LOGGER.error("[MCBG] /mcbg team list failed", t);
                                        ctx.getSource().sendFailure(Component.literal("팀 목록 출력 중 오류(로그 확인): " + t.getClass().getSimpleName()));
                                        return 0;
                                    }
                                }))

                        .then(Commands.literal("add")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ctx -> {
                                            try {
                                                MinecraftServer server = ctx.getSource().getServer();
                                                ServerLevel level = server.getLevel(Level.OVERWORLD);
                                                if (level == null) {
                                                    ctx.getSource().sendFailure(Component.literal("오버월드(level)가 null 입니다."));
                                                    return 0;
                                                }

                                                String team = StringArgumentType.getString(ctx, "team");
                                                BgTeamSavedData td = BgTeamSavedData.get(level);

                                                if (td.hasTeam(team)) {
                                                    ctx.getSource().sendFailure(Component.literal("이미 존재하는 팀입니다: " + team));
                                                    return 0;
                                                }

                                                td.addTeam(team);
                                                BgTeamManager.syncToAll(server);
                                                ctx.getSource().sendSuccess(() -> Component.literal("팀 생성: " + team), true);
                                                return 1;

                                            } catch (Throwable t) {
                                                ysMcbgMod.LOGGER.error("[MCBG] /mcbg team add failed", t);
                                                ctx.getSource().sendFailure(Component.literal("팀 명령 실행 중 오류(로그 확인): " + t.getClass().getSimpleName()));
                                                return 0;
                                            }
                                        })))

                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUGGEST_TEAMS) // 팀 이름 자동완성 추가
                                        .executes(ctx -> {
                                            MinecraftServer server = ctx.getSource().getServer();
                                            ServerLevel level = server.getLevel(Level.OVERWORLD);
                                            if (level == null) return 0;

                                            String team = StringArgumentType.getString(ctx, "team");
                                            BgTeamSavedData td = BgTeamSavedData.get(level);
                                            if (!td.hasTeam(team)) {
                                                ctx.getSource().sendFailure(Component.literal("존재하지 않는 팀입니다: " + team));
                                                return 0;
                                            }
                                            td.removeTeam(team);
                                            BgTeamManager.syncToAll(server);
                                            ctx.getSource().sendSuccess(() -> Component.literal("팀 삭제: " + team), true);
                                            return 1;
                                        })))

                        .then(Commands.literal("join")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUGGEST_TEAMS) // 팀 이름 자동완성 추가
                                        // /mcbg team join <team> (self)
                                        .executes(ctx -> {
                                            MinecraftServer server = ctx.getSource().getServer();
                                            ServerLevel level = server.getLevel(Level.OVERWORLD);
                                            if (level == null) return 0;

                                            String team = StringArgumentType.getString(ctx, "team");
                                            BgTeamSavedData td = BgTeamSavedData.get(level);

                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            td.join(team, sp.getUUID());
                                            BgTeamManager.syncToAll(server);
                                            ctx.getSource().sendSuccess(() -> Component.literal("팀 참가: " + team + " (1)"), true);
                                            return 1;
                                        })
                                        // /mcbg team join <team> <targets>
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> {
                                                    MinecraftServer server = ctx.getSource().getServer();
                                                    ServerLevel level = server.getLevel(Level.OVERWORLD);
                                                    if (level == null) return 0;

                                                    String team = StringArgumentType.getString(ctx, "team");
                                                    BgTeamSavedData td = BgTeamSavedData.get(level);

                                                    int count = 0;
                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                        td.join(team, p.getUUID());
                                                        count++;
                                                    }
                                                    BgTeamManager.syncToAll(server);
                                                    Component msg = Component.literal("팀 참가: " + team + " (" + count + ")");
                                                    ctx.getSource().sendSuccess(() -> msg, true);
                                                    return count;
                                                }))))

                        .then(Commands.literal("leave")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUGGEST_TEAMS) // 팀 이름 자동완성 추가
                                        // /mcbg team leave <team> (self)
                                        .executes(ctx -> {
                                            MinecraftServer server = ctx.getSource().getServer();
                                            ServerLevel level = server.getLevel(Level.OVERWORLD);
                                            if (level == null) return 0;

                                            String team = StringArgumentType.getString(ctx, "team");
                                            BgTeamSavedData td = BgTeamSavedData.get(level);

                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            String cur = td.getPlayerTeam().get(sp.getUUID());
                                            if (cur != null && cur.equals(team)) {
                                                td.leave(sp.getUUID());
                                                BgTeamManager.syncToAll(server);
                                                ctx.getSource().sendSuccess(() -> Component.literal("팀 탈퇴: " + team + " (1)"), true);
                                                return 1;
                                            }
                                            ctx.getSource().sendFailure(Component.literal("이미 해당 팀이 아니야: " + team));
                                            return 0;
                                        })
                                        // /mcbg team leave <team> <targets>
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> {
                                                    MinecraftServer server = ctx.getSource().getServer();
                                                    ServerLevel level = server.getLevel(Level.OVERWORLD);
                                                    if (level == null) return 0;

                                                    String team = StringArgumentType.getString(ctx, "team");
                                                    BgTeamSavedData td = BgTeamSavedData.get(level);

                                                    int count = 0;
                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
                                                        String cur = td.getPlayerTeam().get(p.getUUID());
                                                        if (cur != null && cur.equals(team)) {
                                                            td.leave(p.getUUID());
                                                            count++;
                                                        }
                                                    }
                                                    BgTeamManager.syncToAll(server);
                                                    ctx.getSource().sendSystemMessage(Component.literal("팀 탈퇴 : " + team + " (" + count + ")"));
                                                    return count;
                                                }))))
                )
        );
    }
    /**
     * /mcbg worldborder start [seed]
     * - startGameTime을 현재로 고정
     * - seed 기반으로 "현재 원 안에서 다음 원 중심 랜덤 이동" 플랜 재생성
     */
    private int startZone(CommandSourceStack src, Long seedOpt) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);

        // 새 게임마다 아레나 원본 블록/BlockEntity NBT를 먼저 캡처한다.
        // 실제 자기장(game running)은 스냅샷이 완성된 뒤 시작된다.
        ArenaRollbackManager.captureArena(
                overworld,
                ArenaRollbackManager.DEFAULT_MIN,
                ArenaRollbackManager.DEFAULT_MAX
        );

        data.running = true;
        data.startGameTime = overworld.getGameTime();

        // stage/phase 알림 및 패킷 전송 상태 초기화
        data.lastAnnouncedShrinkPhase = 0;
        data.lastSentPhase = 0;
        data.lastSentStage = -1;

        // seed 설정 + 플랜 재생성
        data.seed = (seedOpt != null) ? seedOpt : java.util.concurrent.ThreadLocalRandom.current().nextLong();
        data.rebuildPlan();

        data.setDirty();

        sendAdminSuccess(src, Component.literal("[MCBG] 월드보더 시작! (seed=" + data.seed + ")"), true);

        // HUD/보더 즉시 갱신
        BgZoneManager.syncToAll(server);
        return 1;
    }

    private int stopZone(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        data.running = false;
        data.setDirty();

        // 커스텀 자기장 종료 = 게임 종료. 원본 아레나를 원상복구한다.
        ArenaRollbackManager.resetArena(overworld);
        ArenaRollbackManager.clearSnapshot();

        // vanilla border 원상복구(기본값 수준)
        overworld.getWorldBorder().setCenter(0.0, 0.0);
        overworld.getWorldBorder().setSize(60000000.0);

        // 클라 HUD 끄기
        ysMcbgMod.CHANNEL.send(PacketDistributor.ALL.noArg(), new ZoneScheduleSyncPacket());

        sendAdminSuccess(src, Component.literal("[MCBG] 월드보더 중지"), true);
        return 1;
    }

    /**
     * /mcbg worldborder set <minutes>
     * - 테스트용: "시작 후 minutes(분) 경과한 상태"로 강제 점프
     */
    private int setElapsed(CommandSourceStack src, int minutes) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        if (!data.running) {
            sendAdminFailure(src, Component.literal("[MCBG] 월드보더가 실행 중이 아닙니다. start 먼저 해주세요."));
            return 0;
        }

        long now = overworld.getGameTime();
        data.startGameTime = now - (minutes * 1200L);

        // 강제 싱크 유도
        data.lastSentStage = -1;
        data.lastSentPhase = 0;
        data.setDirty();

        BgZoneManager.syncToAll(server);

        sendAdminSuccess(src, Component.literal("[MCBG] 시간 점프: 시작 후 " + minutes + "분 경과 상태로 설정"), true);
        return 1;
    }

    private int setWaits(CommandSourceStack src, int[] waits) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        System.arraycopy(waits, 0, data.waitMinutes, 0, 5);
        data.setDirty();

        BgZoneManager.syncToAll(server);
        sendAdminSuccess(src, Component.literal("[MCBG] 대기(분) 설정: " + phasesToString(waits)), true);
        return 1;
    }

    private int setShrinks(CommandSourceStack src, int[] shrinks) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        System.arraycopy(shrinks, 0, data.shrinkMinutes, 0, 5);
        data.setDirty();

        BgZoneManager.syncToAll(server);
        sendAdminSuccess(src, Component.literal("[MCBG] 축소(분) 설정: " + phasesToString(shrinks)), true);
        return 1;
    }

    private int setRadii(CommandSourceStack src, int[] radii) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        for (int i = 0; i < 5; i++) {
            data.endRadius[i] = radii[i];
        }
        data.rebuildPlan();
        data.setDirty();

        BgZoneManager.syncToAll(server);
        sendAdminSuccess(src, Component.literal("[MCBG] 페이즈 최종 반경 설정: " + phasesToString(radii)), true);
        return 1;
    }

    private int setSeed(CommandSourceStack src, long seed) {
        MinecraftServer server = src.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        data.seed = seed;
        data.rebuildPlan();
        data.setDirty();

        BgZoneManager.syncToAll(server);
        sendAdminSuccess(src, Component.literal("[MCBG] seed 변경 + 플랜 재생성 완료! (seed=" + seed + ")"), true);
        return 1;
    }

    private int infoZone(CommandSourceStack src) {
        ServerLevel overworld = src.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        BgZoneSavedData data = BgZoneSavedData.get(overworld);
        if (!data.running) {
            sendAdminSuccess(src, Component.literal("[MCBG] 월드보더: 실행 중 아님"), false);
            return 1;
        }

        ZoneRuntime rt = ZoneRuntime.compute(data, overworld.getGameTime());
        long now = overworld.getGameTime();
        long remainStage = Math.max(0L, rt.stageStartTick + rt.stageDurationTicks - now);

        String stageName = (rt.stage == ZoneRuntime.STAGE_SHRINK) ? "SHRINK" : "WAIT";

        sendAdminSuccess(src, Component.literal(
                "[MCBG] 월드보더 상태: running=true / phase=" + rt.phase + " / stage=" + stageName +
                        " / center=(" + String.format("%.1f", rt.curX) + "," + String.format("%.1f", rt.curZ) + ")" +
                        " / r=" + String.format("%.1f", rt.curR) +
                        " / stageRemain=" + (remainStage / 20) + "s"
        ), false);

        return 1;
    }

    private static String phasesToString(int[] pm) {
        return pm[0] + "," + pm[1] + "," + pm[2] + "," + pm[3] + "," + pm[4];
    }
    private static void syncKillsToClient(ServerPlayer player) {
        int k = KillData.get(player.server).getKills(player.getUUID());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new KillSyncPacket(k));
    }

    /** 서버의 모든 플레이어에게 현재 킬 데이터를 다시 전송 */
    private static void syncKillsToAll(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            syncKillsToClient(sp);
        }
    }

    // =============================
    // DOWNED 유틸 (동기화/정리)
    // =============================
    private static void broadcastDowned(MinecraftServer server, UUID playerId, boolean downed) {
        if (server == null) return;
        CHANNEL.send(PacketDistributor.ALL.noArg(), new DownStatePacket(playerId, downed));
    }

    private static void syncAllDownedToClient(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new DownStatePacket(p.getUUID(), BgPlayerState.isDowned(p)));
        }
    }

    /** 다운 진입 공통 처리 (LivingDeathEvent에서 호출) */
    private static void beginDowned(ServerPlayer victim, ServerPlayer downer) {
        BgPlayerState.setDowned(victim, true);
        BgPlayerState.clearRevive(victim);
        BgPlayerState.setBleedTicks(victim, DownedServerTick.BLEED_TOTAL);

        // 기절시킨 사람(킬 귀속용)
        if (downer != null && downer != victim) {
            BgPlayerState.setDownedBy(victim, downer.getUUID());
        } else {
            BgPlayerState.clearDownedBy(victim);
        }

        // "피 100"으로 세팅(최대체력 임시 확장)
        BgPlayerState.applyDownedMaxHealth(victim);
        victim.setHealth(victim.getMaxHealth());

        // 엎드림(크롤) 강제
        victim.setPose(Pose.SWIMMING);
        victim.setForcedPose(Pose.SWIMMING);
        victim.setSwimming(true);
        victim.refreshDimensions();

        // 킬피드
        broadcastKillFeedDown(victim, downer);

        broadcastDowned(victim.getServer(), victim.getUUID(), true);
    }

    /** 다운 해제(부활/명령어 해제/리스폰 정리) 공통 처리 */
    public static void clearDowned(ServerPlayer p, boolean setHealth10) {
        // Revive HUD를 끄기 위해 먼저 reviver를 캡쳐
        java.util.UUID reviverId = BgPlayerState.getReviver(p);

        BgPlayerState.setDowned(p, false);
        BgPlayerState.setBleedTicks(p, 0);
        BgPlayerState.clearRevive(p);
        BgPlayerState.clearDownedBy(p);
        BgPlayerState.clearDownedMaxHealth(p);

        // HUD 숨김(다운된 사람 + 살리던 사람)
        sendReviveProgress(p, false, 0, 0);
        if (reviverId != null && p.getServer() != null) {
            ServerPlayer reviver = p.getServer().getPlayerList().getPlayer(reviverId);
            if (reviver != null) sendReviveProgress(reviver, false, 0, 0);
        }

        p.setForcedPose(null);
        p.setSwimming(false);
        p.refreshDimensions();

        if (setHealth10) {
            p.setHealth(Math.min(10.0f, p.getMaxHealth()));
        }

        broadcastDowned(p.getServer(), p.getUUID(), false);
    }

    // ==========================================================
// ENDGAME / TEAM LOGIC (MOD TEAM ONLY)
// ==========================================================

    /**
     * "다운"(기절) 대신 즉사 처리해야 하는 상황인지 판단.
     * - 남은 '유효한 팀'이 한 팀뿐(=상대팀이 0)이라면, 더 이상 소생/기절 시스템을 쓰지 않고 즉사.
     * - 팀이 없는(FFA) 구성이라면 "남은 생존 플레이어가 1명"일 때 즉사.
     */
    private static boolean shouldDownInsteadOfDie(ServerPlayer victim) {
        try {
            MinecraftServer server = victim.getServer();
            if (server == null) return true; // 서버 못 잡으면 안전하게 다운(원하면 false로 바꿔도 됨)

            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return true;

            BgTeamSavedData td = BgTeamSavedData.get(level);
            if (td == null) return true;

            String myTeam = td.getPlayerTeam().get(victim.getUUID());

            // 팀이 없으면(FFA) 무조건 즉사 = 다운하지 않음
            if (myTeam == null || myTeam.isBlank()) {
                return false;
            }

            // 같은 팀 "살아있는 플레이어"가 있으면 다운
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p == null) continue;
                if (p == victim) continue;
                if (p.isSpectator() || p.isCreative()) continue;
                if (!p.isAlive()) continue;
                if (BgPlayerState.isDowned(p)) continue; // 기절 상태는 '살아있는 팀원'으로 안침

                String t = td.getPlayerTeam().get(p.getUUID());
                if (myTeam.equals(t)) {
                    return true; // 같은 팀 생존자 존재 -> 다운
                }
            }

            // 같은 팀 생존자 없음 -> 즉사
            return false;

        } catch (Throwable t) {
            // 예외 시 기존 동작 유지(다운)
            return true;
        }
    }

    /**
     * 팀킬 판정: 바닐라 스코어보드 팀은 절대 보지 않고, 모드 팀(BgTeamSavedData)만 사용.
     */
    private static boolean isTeamKill(ServerPlayer attacker, ServerPlayer victim) {
        try {
            if (attacker == null || victim == null) return false;
            MinecraftServer server = attacker.getServer();
            if (server == null) return false;
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) return false;

            BgTeamSavedData td = BgTeamSavedData.get(level);
            String a = td.getPlayerTeam().get(attacker.getUUID());
            String b = td.getPlayerTeam().get(victim.getUUID());
            return a != null && !a.isBlank() && a.equals(b);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * DamageSource에서 킬러 플레이어를 최대한 안정적으로 찾아냄
     * - 일반 근접/폭발/간접피해: source.getEntity()
     * - 일부 발사체: source.getDirectEntity()가 Projectile이고 getOwner()가 플레이어
     */
    private static ServerPlayer resolveKiller(LivingDeathEvent event) {
        Entity src = event.getSource().getEntity();
        if (src instanceof ServerPlayer sp) return sp;

        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner instanceof ServerPlayer sp2) return sp2;
        }

        return null;
    }

    // =============================
    // Killfeed (chat)
    // =============================

    private static Component teamTag(MinecraftServer server, UUID playerId) {
        if (server == null) return Component.literal("[?]").withStyle(ChatFormatting.GRAY);
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return Component.literal("[?]").withStyle(ChatFormatting.GRAY);

        BgTeamSavedData td = BgTeamSavedData.get(level);
        String team = td.getPlayerTeam().get(playerId);
        if (team == null || team.isBlank()) {
            return Component.literal("[Solo]").withStyle(ChatFormatting.DARK_GRAY);
        }

        int argb = td.getColor(team, 0xFFAAAAAA);
        return Component.literal("[" + team + "]")
                .withStyle(s -> s.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
    }

    private static void broadcastKillFeedDown(ServerPlayer victim, ServerPlayer downer) {
        MinecraftServer server = victim.getServer();
        if (server == null) return;

        Component msg;
        if (downer != null) {
            msg = Component.empty()
                    .append(teamTag(server, victim.getUUID())).append(" ")
                    .append(victim.getDisplayName()).append(Component.literal(" 님이 "))
                    .append(teamTag(server, downer.getUUID())).append(" ")
                    .append(downer.getDisplayName())
                    .append(Component.literal(" 님에 의해 기절했습니다!"));
        } else {
            msg = Component.empty()
                    .append(teamTag(server, victim.getUUID())).append(" ")
                    .append(victim.getDisplayName())
                    .append(Component.literal(" 님이 기절했습니다!"));
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    private static void broadcastKillFeedDeath(ServerPlayer victim, ServerPlayer creditedPlayer) {
        MinecraftServer server = victim.getServer();
        if (server == null) return;

        Component msg;
        if (creditedPlayer != null) {
            msg = Component.empty()
                    .append(teamTag(server, victim.getUUID())).append(" ")
                    .append(victim.getDisplayName()).append(Component.literal(" 님이 "))
                    .append(teamTag(server, creditedPlayer.getUUID())).append(" ")
                    .append(creditedPlayer.getDisplayName())
                    .append(Component.literal(" 님에 의해 사망했습니다!"));
        } else {
            msg = Component.empty()
                    .append(teamTag(server, victim.getUUID())).append(" ")
                    .append(victim.getDisplayName())
                    .append(Component.literal(" 님이 사망했습니다!"));
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}

// ============================================================================
// 아래 클래스들은 파일을 늘리지 않으려고 같은 파일에 넣어둠 (package-private)
// ============================================================================

/** 클라이언트 HUD가 읽는 킬 캐시 (서버에서 패킷으로 갱신) */
class ClientKillCache {
    public static int killCount = 0;
}

/** 서버 -> 클라: "내 킬" 값만 동기화 */
class KillSyncPacket {
    private final int kills;

    public KillSyncPacket(int kills) {
        this.kills = kills;
    }

    public KillSyncPacket(net.minecraft.network.FriendlyByteBuf buf) {
        this.kills = buf.readVarInt();
    }

    public void toBytes(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(this.kills);
    }

    public boolean handle(java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
        net.minecraftforge.network.NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> ClientKillCache.killCount = this.kills);
        ctx.setPacketHandled(true);
        return true;
    }
}

/** 월드 저장(서버)에 남는 플레이어별 킬 카운트 */
class KillData extends SavedData {
    private static final String NAME = "ys_mcbg_kills";
    private final Map<UUID, Integer> kills = new HashMap<>();

    public static KillData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(KillData::load, KillData::new, NAME);
    }

    public static KillData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(KillData::load, KillData::new, NAME);
    }

    public int getKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public void addKills(UUID uuid, int add) {
        kills.put(uuid, getKills(uuid) + add);
        setDirty();
    }

    public void reset(UUID uuid) {
        kills.put(uuid, 0);
        setDirty();
    }

    public void set(UUID uuid, int value) {
        kills.put(uuid, Math.max(0, value));
        setDirty();
    }

    /** 모든 킬 데이터 초기화 */
    public void clearAll() {
        kills.clear();
        setDirty();
    }

    public static KillData load(CompoundTag tag) {
        KillData data = new KillData();
        CompoundTag map = tag.getCompound("kills");
        for (String key : map.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.kills.put(uuid, map.getInt(key));
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag map = new CompoundTag();
        for (var e : kills.entrySet()) {
            map.putInt(e.getKey().toString(), e.getValue());
        }
        tag.put("kills", map);
        return tag;
    }
}
