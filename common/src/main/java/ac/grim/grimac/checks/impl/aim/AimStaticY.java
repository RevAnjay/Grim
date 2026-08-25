package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimStaticY", configName = "AimStaticY", description = "Detects flat vertical rotation without horizontal adjustment")
public class AimStaticY extends Check implements RotationListener {
    private int consecutiveNoYawChangeThreshold = 20;
    private double minVerticalRotationThreshold = 5.0;

    private int consecutiveNoYawChanges = 0;
    private double accumulatedVerticalRotation = 0.0;

    public AimStaticY(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        consecutiveNoYawChangeThreshold = config.getIntElse("AimStaticY.consecutive-threshold", 20);
        minVerticalRotationThreshold = config.getDoubleElse("AimStaticY.min-vertical-threshold", 5.0);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.compensatedEntities.self.getRiding() != null) {
            reset();
            return;
        }

        float deltaYaw = Math.abs(rotationUpdate.getDeltaXRot());
        float deltaPitch = rotationUpdate.getDeltaYRot();

        if (deltaYaw < 0.1f && Math.abs(deltaPitch) > 0.1f) {
            consecutiveNoYawChanges++;
            accumulatedVerticalRotation += Math.abs(deltaPitch);
        } else {
            reset();
        }

        if (consecutiveNoYawChanges >= consecutiveNoYawChangeThreshold &&
                accumulatedVerticalRotation >= minVerticalRotationThreshold) {
            flagAndAlert(String.format("ticks=%d vertical=%.1f", consecutiveNoYawChanges, accumulatedVerticalRotation));
            reset();
        }
    }

    private void reset() {
        consecutiveNoYawChanges = 0;
        accumulatedVerticalRotation = 0.0;
    }
}
