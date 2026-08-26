package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
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

@CheckData(name = "CombatStrafe", configName = "CombatStrafe", description = "Detects silent killaura move-fix by checking if movement angle deviates from all 8 vanilla WASD vectors", decay = 0.05)
public class CombatStrafe extends Check implements PacketReceiveListener {

    private static final Verbose V = Verbose.of("diff={f32}, spd={f32}");

    // The 8 possible movement angles relative to look yaw in vanilla Minecraft
    private static final float[] VANILLA_DIRECTIONS = {
            0.0f,    // W
            45.0f,   // W + D
            -45.0f,  // W + A
            90.0f,   // D
            -90.0f,  // A
            135.0f,  // S + D
            -135.0f, // S + A
            180.0f   // S
    };

    private double maxAngleDeviation = 25.0;
    private double minSpeed = 0.20;
    private double buffer = 0;
    private double maxBuffer = 4.0;
    private boolean cancelHits = true;

    public CombatStrafe(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxAngleDeviation = config.getDoubleElse(getConfigName() + ".max-angle-deviation", 25.0);
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

            float rawDiff = (moveYaw - attackYaw) % 360.0f;
            if (rawDiff >= 180.0f) rawDiff -= 360.0f;
            if (rawDiff < -180.0f) rawDiff += 360.0f;

            // Find minimum deviation from any valid vanilla 8-way movement direction
            float minDeviation = 180.0f;
            for (float vanillaDir : VANILLA_DIRECTIONS) {
                float dev = Math.abs(rawDiff - vanillaDir);
                if (dev > 180.0f) dev = 360.0f - dev;
                if (dev < minDeviation) {
                    minDeviation = dev;
                }
            }

            // If the movement direction does not align with ANY vanilla movement direction (Silent Aura move-fix)
            if (minDeviation > maxAngleDeviation) {
                if (++buffer > maxBuffer) {
                    if (flag(V.write(verbose()).f32(minDeviation).f32((float) horizontalSpeed))) {
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
