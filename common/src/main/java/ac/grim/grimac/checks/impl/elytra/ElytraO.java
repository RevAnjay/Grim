package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "ElytraO", description = "Regular gliding-toggle pattern (swap-fly cheats)", experimental = true, decay = 0.05, setback = 3)
public class ElytraO extends Check implements PacketCheck {

    private static final int BUF = 8;
    private static final int MIN_TOGGLES = 6;
    private static final int MIN_AIR_TICKS = 40;
    private static final double SD_THRESHOLD_MS = 40.0;
    // Floor matches client tick (50ms) — Spigot doesn't rate-limit ENTITY_ACTION, so a tick-perfect
    // cheat can drive toggles down to one client tick. Anything below this is unreachable.
    private static final double MEAN_MIN_MS = 50.0;
    private static final double MEAN_MAX_MS = 750.0;
    private static final double CP_RATIO_THRESHOLD = 0.65;
    private static final double FLAG_BUFFER = 3.0;

    private final long[] toggleTimes = new long[BUF];
    private int toggleCount;

    private int airTicks;
    private int elytraTicks;
    private int nonElytraTicks;

    private double buffer;

    public ElytraO(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (player.onGround || player.wasTouchingWater
                    || player.packetStateData.lastPacketWasTeleport) {
                airTicks = 0;
                elytraTicks = 0;
                nonElytraTicks = 0;
                toggleCount = 0;
                buffer = Math.max(0.0, buffer - 0.05);
                return;
            }

            airTicks++;
            if (player.inventory.getChestplate().getType() == ItemTypes.ELYTRA) {
                elytraTicks++;
            } else {
                nonElytraTicks++;
            }

            buffer = Math.max(0.0, buffer - 0.02);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            var action = new WrapperPlayClientEntityAction(event);
            if (action.getAction() != WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA) return;

            // High-ping data is unreliable for timing analysis
            if (player.getTransactionPing() > 500) return;

            toggleTimes[toggleCount % BUF] = System.currentTimeMillis();
            toggleCount = (toggleCount + 1) & 0x3FFFFFFF;

            evaluatePattern();
        }
    }

    private void evaluatePattern() {
        if (toggleCount < MIN_TOGGLES) return;
        if (airTicks < MIN_AIR_TICKS) return;

        int n = Math.min(toggleCount, BUF);
        // Reconstruct chronological order from ring buffer
        long[] times = new long[n];
        int start = toggleCount - n;
        for (int i = 0; i < n; i++) {
            times[i] = toggleTimes[(start + i) % BUF];
        }

        double sum = 0;
        int intervals = n - 1;
        for (int i = 1; i < n; i++) {
            sum += times[i] - times[i - 1];
        }
        double mean = sum / intervals;

        double sqDiff = 0;
        for (int i = 1; i < n; i++) {
            double diff = (times[i] - times[i - 1]) - mean;
            sqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sqDiff / intervals);

        boolean regular = stdDev < SD_THRESHOLD_MS && mean >= MEAN_MIN_MS && mean <= MEAN_MAX_MS;
        double cpRatio = (double) nonElytraTicks / (double) airTicks;
        boolean cpDominant = cpRatio > CP_RATIO_THRESHOLD;

        if (regular && cpDominant) {
            buffer += 1.0;
            if (buffer > FLAG_BUFFER) {
                if (flagAndAlert(String.format("mean=%.0fms sd=%.1fms cp=%.2f", mean, stdDev, cpRatio))) {
                    setbackIfAboveSetbackVL();
                }
            }
        }
    }
}
