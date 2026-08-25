package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimAcceleration", configName = "AimAcceleration", description = "Detects sudden rotations beyond human capability")
public class AimAcceleration extends Check implements RotationListener {
    private double accelerationThreshold = 900.0;
    private float lastDeltaYaw = 0.0f;

    public AimAcceleration(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        accelerationThreshold = config.getDoubleElse("AimAcceleration.threshold", 900.0);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.compensatedEntities.self.getRiding() != null) {
            lastDeltaYaw = rotationUpdate.getDeltaXRot();
            return;
        }

        float deltaYaw = rotationUpdate.getDeltaXRot();
        double acceleration = Math.abs(deltaYaw - lastDeltaYaw);
        if (acceleration > accelerationThreshold) {
            flagAndAlert(String.format("accel=%.1f", acceleration));
        }

        lastDeltaYaw = deltaYaw;
    }
}
