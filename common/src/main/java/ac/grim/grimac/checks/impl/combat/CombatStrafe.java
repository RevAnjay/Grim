package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "CombatStrafe", configName = "CombatStrafe", description = "Detects silent killaura move-fix by comparing velocity direction against attack yaw", decay = 0.05)
public class CombatStrafe extends Check implements PacketReceiveListener {

    private double maxAngleDiff = 75.0;
    private double minSpeed = 0.20; // Blocks per tick (~4.0 m/s)
    private double buffer = 0;
    private double maxBuffer = 4.0;
    private boolean cancelHits = true;

    public CombatStrafe(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxAngleDiff = config.getDoubleElse(getConfigName() + ".max-angle-diff", 75.0);
        minSpeed = config.getDoubleElse(getConfigName() + ".min-speed", 0.20);
        maxBuffer = config.getDoubleElse(getConfigName() + ".buffer", 4.0);
        cancelHits = config.getBooleanElse(getConfigName() + ".cancel-hits", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

            if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
            if (player.inVehicle()) return;
            if (player.isGliding || player.wasGliding) return;
            if (System.currentTimeMillis() - player.joinTime < 5000) return;
            if (player.packetStateData.lastPacketWasTeleport) return;

            // Ignore if player received knockback or explosion velocity recently
            if (player.predictedVelocity.isExplosion() || player.predictedVelocity.isKnockback()) {
                buffer = Math.max(0, buffer - 0.5);
                return;
            }

            PacketEntity entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
            if (entity == null || !entity.canHit() || !entity.isLivingEntity) return;

            // Calculate horizontal speed
            double vx = player.x - player.lastX;
            double vz = player.z - player.lastZ;
            double horizontalSpeed = Math.hypot(vx, vz);

            if (horizontalSpeed < minSpeed) {
                buffer = Math.max(0, buffer - 0.1);
                return;
            }

            // Calculate movement vector angle in degrees [-180, 180]
            float moveYaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
            float attackYaw = player.yaw;
            float diff = (moveYaw - attackYaw) % 360.0f;
            if (diff >= 180.0f) diff -= 360.0f;
            if (diff < -180.0f) diff += 360.0f;
            float angleDiff = Math.abs(diff);
            // If player moves at full forward speed but attacks in angle > maxAngleDiff without proper strafe deceleration
            if (angleDiff > maxAngleDiff) {
                if (++buffer > maxBuffer) {
                    if (flag(String.format("Move-fix discrepancy angleDiff=%.1f > %.1f speed=%.2f", angleDiff, maxAngleDiff, horizontalSpeed))) {
                        if (cancelHits) {
                            player.cancelCombatTicks = 10;
                        }
                    }
                }
            } else {
                buffer = Math.max(0, buffer - 0.2);
            }
        }
    }
}
