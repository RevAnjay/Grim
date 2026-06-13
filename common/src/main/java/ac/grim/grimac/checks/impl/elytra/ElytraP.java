package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;

@CheckData(name = "ElytraP", description = "Sustained gliding-style velocity without a glider equipped", experimental = true, decay = 0.05, setback = 3)
public class ElytraP extends Check implements PostPredictionCheck {

    // Wait long enough that elytra-flight residual inertia (drag 0.99) has decayed under vanilla
    // drag (0.91) — at 8 ticks initial 1.5 m/tick is below 0.7 already.
    private static final int MIN_NON_GLIDER_TICKS = 8;
    private static final double IMPULSE_THRESHOLD = 0.04;
    private static final int IMPULSE_STREAK_REQUIRED = 3;
    private static final double SUSTAINED_SPEED_THRESHOLD = 0.7;
    private static final int SUSTAINED_TICKS_REQUIRED = 8;
    private static final int LOOK_DOT_WINDOW = 6;
    private static final double LOOK_DOT_MEAN_REQUIRED = 0.97;

    private int nonGliderAirTicks;
    private double previousHorizontalSpeed;
    private int impulseStreak;
    private int sustainedSpeedTicks;
    // Ring buffer of horizontal velocity vs look dot products
    private final double[] lookDotBuffer = new double[LOOK_DOT_WINDOW];
    private int lookDotIndex;
    private int lookDotFilled;
    private double buffer;

    public ElytraP(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        if (player.onGround
                || player.lastOnGround
                || player.wasTouchingWater
                || player.wasSwimming
                || player.isFlying
                || player.inVehicle()
                || player.isClimbing
                || player.verticalCollision
                || player.horizontalCollision
                || player.packetStateData.lastPacketWasTeleport
                || player.predictedVelocity.isKnockback()
                || player.predictedVelocity.isExplosion()
                || player.packetStateData.tryingToRiptide
                || player.compensatedEntities.getSlowFallingAmplifier().isPresent()
                || player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.LEVITATION).isPresent()
                || player.getTransactionPing() > 500) {
            reset();
            return;
        }

        boolean hasElytraChest = player.inventory.getChestplate().getType() == ItemTypes.ELYTRA;
        if (hasElytraChest || player.canGlide()) {
            reset();
            return;
        }

        nonGliderAirTicks++;

        double dx = player.x - player.lastX;
        double dz = player.z - player.lastZ;
        double currentHorizontalSpeed = Math.hypot(dx, dz);

        if (nonGliderAirTicks < MIN_NON_GLIDER_TICKS) {
            previousHorizontalSpeed = currentHorizontalSpeed;
            return;
        }

        // Impulse signal: how much faster than vanilla drag (0.91) the player is moving
        double expected = 0.91 * previousHorizontalSpeed;
        double impulse = currentHorizontalSpeed - expected;
        if (impulse > IMPULSE_THRESHOLD && previousHorizontalSpeed > 0.3) {
            impulseStreak++;
        } else {
            impulseStreak = 0;
        }

        // Sustained speed signal
        if (currentHorizontalSpeed > SUSTAINED_SPEED_THRESHOLD) {
            sustainedSpeedTicks++;
        } else {
            sustainedSpeedTicks = 0;
        }

        // Look-velocity alignment dot product (only when meaningful speed)
        double lookDot = -2.0;
        if (currentHorizontalSpeed > 0.3) {
            float yawRad = (float) Math.toRadians(player.yaw);
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            lookDot = (dx * lookX + dz * lookZ) / currentHorizontalSpeed;
            lookDotBuffer[lookDotIndex] = lookDot;
            lookDotIndex = (lookDotIndex + 1) % LOOK_DOT_WINDOW;
            if (lookDotFilled < LOOK_DOT_WINDOW) lookDotFilled++;
        } else {
            lookDotFilled = 0;
            lookDotIndex = 0;
        }

        boolean impulseDetected = impulseStreak >= IMPULSE_STREAK_REQUIRED;

        boolean lookSustained = false;
        if (lookDotFilled >= LOOK_DOT_WINDOW && sustainedSpeedTicks >= SUSTAINED_TICKS_REQUIRED) {
            double sum = 0;
            for (double d : lookDotBuffer) sum += d;
            double mean = sum / LOOK_DOT_WINDOW;
            lookSustained = mean > LOOK_DOT_MEAN_REQUIRED;
        }

        if (impulseDetected || lookSustained) {
            buffer += 1.0;
            if (buffer > 3.0) {
                String reason = impulseDetected
                        ? String.format("impulse v=%.2f→%.2f imp=%.3f streak=%d",
                                previousHorizontalSpeed, currentHorizontalSpeed, impulse, impulseStreak)
                        : String.format("sustained v=%.2f ticks=%d dot=%.3f", currentHorizontalSpeed,
                                sustainedSpeedTicks, lookDot);
                if (flagAndAlert(reason)) {
                    setbackIfAboveSetbackVL();
                }
            }
        } else {
            buffer = Math.max(0, buffer - 0.25);
        }

        previousHorizontalSpeed = currentHorizontalSpeed;
    }

    private void reset() {
        nonGliderAirTicks = 0;
        previousHorizontalSpeed = 0;
        impulseStreak = 0;
        sustainedSpeedTicks = 0;
        lookDotIndex = 0;
        lookDotFilled = 0;
        buffer = Math.max(0, buffer - 0.1);
    }
}
