package ac.grim.grimac.utils.latency;

import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.player.GameMode;

public class FreezeDetector {

    private static final int RTT_BASELINE_SAMPLES = 20;
    private static final long JOIN_GRACE_MS = 5000;

    private final GrimPlayer player;

    private volatile long lastFlyingPacketMs = System.currentTimeMillis();
    private volatile long gapStartMs = 0;
    private volatile boolean inGap = false;
    private volatile int attackPacketsDuringGap = 0;
    private volatile int recoveryFlyingsInGap = 0;
    private volatile long lastUnfreezeMs = 0;
    private volatile int consecutiveNormalFlyings = 0;
    private volatile boolean sawInboundDuringGap = false;
    private volatile int transSentAtGapStart = 0;

    private final long[] rttSamples = new long[RTT_BASELINE_SAMPLES];
    private volatile int rttIndex = 0;
    private volatile int rttFilled = 0;

    public int gapThresholdMs = 1500;
    public int gapStrongThresholdMs = 2000;
    public int rttSpikeThresholdMs = 300;
    public int recoveryThreshold = 6;
    public int scoreCancel = 3;
    public int scoreFlag = 5;
    public int recoveryFlyingsRequired = 5;
    public int postUnfreezeCooldownMs = 1500;

    public FreezeDetector(GrimPlayer player) {
        this.player = player;
    }

    public synchronized void onFlyingPacket() {
        if (isExempt()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        long gap = now - lastFlyingPacketMs;

        if (gap > gapThresholdMs && !inGap) {
            inGap = true;
            gapStartMs = lastFlyingPacketMs;
            recoveryFlyingsInGap = 0;
            onGapEntered();
        }

        if (inGap) recoveryFlyingsInGap++;

        if (gap < 70) {
            consecutiveNormalFlyings++;
            if (consecutiveNormalFlyings >= recoveryFlyingsRequired && inGap) {
                lastUnfreezeMs = now;
                inGap = false;
                attackPacketsDuringGap = 0;
                recoveryFlyingsInGap = 0;
                sawInboundDuringGap = false;
            }
        } else {
            consecutiveNormalFlyings = 0;
        }

        lastFlyingPacketMs = now;
    }

    public synchronized void onTransactionReceived(long rttMs) {
        rttSamples[rttIndex] = rttMs;
        rttIndex = (rttIndex + 1) % RTT_BASELINE_SAMPLES;
        if (rttFilled < RTT_BASELINE_SAMPLES) rttFilled++;

        // S5: a PONG answering a PING SENT during the freeze (id past the gap-start snapshot) AND landing while
        // movement is still withheld (no flying for >gapThreshold) proves the socket is alive while position is
        // frozen - impossible on a single in-order TCP stream for real lag (its flyings would arrive before any
        // PONG), so it only fires for a selective FakeLag, never a genuine HOL-frozen lagger or recovery flush.
        if (inGap
                && player.lastTransactionReceived.get() > transSentAtGapStart
                && System.currentTimeMillis() - lastFlyingPacketMs > gapThresholdMs) {
            sawInboundDuringGap = true;
        }
    }

    public synchronized void onAttackPacket() {
        long now = System.currentTimeMillis();
        long gap = now - lastFlyingPacketMs;
        if (!inGap && gap > gapThresholdMs && !isExempt()) {
            inGap = true;
            gapStartMs = lastFlyingPacketMs;
            onGapEntered();
        }
        if (inGap) attackPacketsDuringGap++;
    }

    // Reset the S5 latch and snapshot the outbound transaction id at gap start, so onTransactionReceived can tell
    // a fresh PING confirmed during the freeze from a stale in-flight PONG sent before it.
    private void onGapEntered() {
        sawInboundDuringGap = false;
        transSentAtGapStart = player.lastTransactionSent.get();
    }

    public boolean isFrozen() {
        return getScore() >= scoreCancel;
    }

    public boolean shouldFlag() {
        // require S5 (proof of liveness during the freeze) so a real lagger's buffered-attack +2 can't escalate to
        // a flag/setback on its own - only a socket-alive-while-movement-frozen FakeLag clears this gate
        return getScore() >= scoreFlag && sawInboundDuringGap;
    }

    public synchronized int getScore() {
        if (isExempt()) return 0;

        int score = 0;
        long now = System.currentTimeMillis();
        long currentGap = inGap ? now - gapStartMs : Math.max(0, now - lastFlyingPacketMs);

        if (currentGap > 5000) return 0;

        if (currentGap > gapStrongThresholdMs) score += 2;
        else if (currentGap > gapThresholdMs) score += 1;

        if (rttSpike()) score += 1;
        if (inGap && recoveryFlyingsInGap >= recoveryThreshold) score += 1;
        if (attackPacketsDuringGap > 0) score += 2;

        if (lastUnfreezeMs > 0 && now - lastUnfreezeMs < postUnfreezeCooldownMs) {
            score = Math.max(score, 1);
        }

        return score;
    }

    public boolean recentlyUnfroze(int ms) {
        return lastUnfreezeMs > 0 && System.currentTimeMillis() - lastUnfreezeMs < ms;
    }

    public long getCurrentGapMs() {
        long now = System.currentTimeMillis();
        return inGap ? now - gapStartMs : Math.max(0, now - lastFlyingPacketMs);
    }

    public int getAttacksDuringGap() {
        return attackPacketsDuringGap;
    }

    public synchronized void reset() {
        inGap = false;
        gapStartMs = 0;
        attackPacketsDuringGap = 0;
        recoveryFlyingsInGap = 0;
        consecutiveNormalFlyings = 0;
        sawInboundDuringGap = false;
        lastFlyingPacketMs = System.currentTimeMillis();
    }

    public long getLastFlyingMs() {
        return lastFlyingPacketMs;
    }

    private boolean rttSpike() {
        if (rttFilled < 5) return false;
        long sum = 0;
        long latest = rttSamples[(rttIndex - 1 + RTT_BASELINE_SAMPLES) % RTT_BASELINE_SAMPLES];
        for (int i = 0; i < rttFilled; i++) sum += rttSamples[i];
        if (rttFilled > 1) sum -= latest;
        long avg = rttFilled > 1 ? sum / (rttFilled - 1) : sum;
        return latest > avg + rttSpikeThresholdMs;
    }

    private boolean isExempt() {
        if (player.gamemode == GameMode.SPECTATOR) return true;
        if (System.currentTimeMillis() - player.joinTime < JOIN_GRACE_MS) return true;
        if (player.packetStateData.lastPacketWasTeleport) return true;
        if (player.disableGrim) return true;
        if (player.inVehicle()) return true;
        if (player.isInBed) return true;
        if (player.compensatedEntities.self.isDead) return true;
        if (!player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport) return true;
        return false;
    }
}
