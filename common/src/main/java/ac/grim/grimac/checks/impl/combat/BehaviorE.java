package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "BehaviorE", configName = "Behavior", description = "Checks for consistent fallDistance on attack", experimental = true)
public class BehaviorE extends Check implements PacketCheck {

    private double lastFallDistance = -1;
    private double buffer = 0;
    private long lastAttackTime = 0;
    private boolean playersOnly = true;
    private boolean debug = false;
    private int bufferThreshold = 25;

    public BehaviorE(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        playersOnly = config.getBooleanElse("Behavior.e.players-only", true);
        debug = config.getBooleanElse("Behavior.e.debug", false);
        bufferThreshold = config.getIntElse("Behavior.e.buffer-threshold", 25);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity action = new WrapperPlayClientInteractEntity(event);
        if (action.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (playersOnly) {
            PacketEntity entity = player.compensatedEntities.entityMap.get(action.getEntityId());
            if (entity == null || entity.type != EntityTypes.PLAYER) return;
        }

        if (player.onGround || player.packetStateData.packetPlayerOnGround) return;

        long now = System.currentTimeMillis();
        long interval = now - lastAttackTime;
        lastAttackTime = now;

        if (interval <= 250 && lastAttackTime != 0) return;

        if (player.verticalCollision
                || player.wasTouchingWater
                || player.wasTouchingLava
                || player.isSwimming
                || player.isClimbing
                || player.isGliding
                || player.isFlying
                || player.gamemode == GameMode.CREATIVE
                || player.gamemode == GameMode.SPECTATOR
                || player.inVehicle()
                || player.compensatedEntities.self.isDead
                || player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.tryingToRiptide
                || player.compensatedEntities.getSlowFallingAmplifier().isPresent()
                || player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.LEVITATION).isPresent()
                || player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.JUMP_BOOST).isPresent()
                || player.stuckSpeedMultiplier.getX() < 0.99
                || player.firstBreadKB != null || player.likelyKB != null
                || player.firstBreadExplosion != null || player.likelyExplosions != null
                || System.currentTimeMillis() - player.joinTime < 5000) {
            buffer = 0;
            lastFallDistance = -1;
            return;
        }

        double fd = player.fallDistance;

        if (lastFallDistance >= 0 && Math.abs(fd - lastFallDistance) < 1.0E-6) {
            buffer++;
            if (debug) {
                LogUtil.info("[BehaviorE] " + player.getName()
                        + String.format(" buffer=%.0f fd=%.6f delta=%.2E", buffer, fd, Math.abs(fd - lastFallDistance)));
            }
            if (buffer > bufferThreshold) {
                flagAndAlert(String.format("fd=%.6f", fd));
                buffer = bufferThreshold - 5;
            }
        } else {
            buffer = buffer / 2;
        }

        lastFallDistance = fd;
    }
}
