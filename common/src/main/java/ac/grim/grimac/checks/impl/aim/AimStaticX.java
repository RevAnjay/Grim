package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimStaticX", configName = "AimStaticX", description = "Detects flat horizontal rotation without vertical adjustment")
public class AimStaticX extends Check implements RotationListener {
    private int consecutiveNoPitchChangeThreshold = 20;
    private double minHorizontalRotationThreshold = 5.0;

    private int consecutiveNoPitchChanges = 0;
    private double accumulatedHorizontalRotation = 0.0;

    public AimStaticX(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        consecutiveNoPitchChangeThreshold = config.getIntElse("AimStaticX.consecutive-threshold", 20);
        minHorizontalRotationThreshold = config.getDoubleElse("AimStaticX.min-horizontal-threshold", 5.0);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.compensatedEntities.self.getRiding() != null) {
            reset();
            return;
        }

        float deltaPitch = Math.abs(rotationUpdate.getDeltaYRot());
        float deltaYaw = rotationUpdate.getDeltaXRot();

        if (deltaPitch < 0.1f && Math.abs(deltaYaw) > 0.1f) {
            consecutiveNoPitchChanges++;
            accumulatedHorizontalRotation += Math.abs(deltaYaw);
        } else {
            reset();
        }

        if (consecutiveNoPitchChanges >= consecutiveNoPitchChangeThreshold &&
                accumulatedHorizontalRotation >= minHorizontalRotationThreshold) {
            flagAndAlert(String.format("ticks=%d horizontal=%.1f", consecutiveNoPitchChanges, accumulatedHorizontalRotation));
            reset();
        }
    }

    private void reset() {
        consecutiveNoPitchChanges = 0;
        accumulatedHorizontalRotation = 0.0;
    }
}
