package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PrePredictionPacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.MovementCheckRunner;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.math.VectorUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

@CheckData(name = "CrashA", stableKey = "grim.crash.large_position", description = "Sent a position outside the valid world bounds")
public class CrashA extends Check implements PrePredictionPacketReceiveListener {
    private static final double HARD_CODED_BORDER = 2.9999999E7D;

    // The last position the client claimed on the wire, tracked across accepted, cancelled and exempt
    // packets. The gate measures each move against this rather than player.x (which freezes whenever a
    // packet is cancelled), so a legit burst of buffered movement reads as N small single-tick deltas
    // instead of one accumulated multi-tick jump. Netty-thread only.
    private Vector3d lastWire = null;

    // Diagnostic switch (CrashA.debug). Logs the raw numbers behind every unrecognised jump so the root
    // cause - why the teleport was not accepted - is visible from one arena log. Off by default.
    private boolean debug = false;

    public CrashA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        debug = config.getBooleanElse("CrashA.debug", false);
    }

    @Override
    public void onPrePredictionPacketReceive(PacketReceiveEvent event) {
        if (player.disableGrim) return;

        final PacketTypeCommon type = event.getPacketType();
        final boolean flying = WrapperPlayClientPlayerFlying.isFlying(type);
        final boolean vehicle = type == PacketType.Play.Client.VEHICLE_MOVE;
        if (!flying && !vehicle) return;

        final double rawX, rawY, rawZ;
        final boolean hasRotation;
        if (flying) {
            WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
            if (!packet.hasPositionChanged()) return; // rotation-only / onground-only carry no position
            rawX = packet.getLocation().getX();
            rawY = packet.getLocation().getY();
            rawZ = packet.getLocation().getZ();
            hasRotation = packet.hasRotationChanged();
        } else {
            Vector3d pos = new WrapperPlayClientVehicleMove(event).getPosition();
            rawX = pos.getX();
            rawY = pos.getY();
            rawZ = pos.getZ();
            hasRotation = false;
        }

        // Leave non-finite coords to CrashC and don't poison the wire reference with a NaN, which would
        // make every later delta NaN and silently disable the gate.
        if (!Double.isFinite(rawX) || !Double.isFinite(rawY) || !Double.isFinite(rawZ)) return;

        final Vector3d clamped = VectorUtils.clampVector(new Vector3d(rawX, rawY, rawZ));

        // Before both gates: otherwise lastWire stays pre-teleport and the next packet reads as a jump.
        if (player.packetStateData.lastPacketWasTeleport) {
            lastWire = clamped;
            return;
        }

        // Absolute crash domain (flying only): a raw coord beyond the world border is impossible (the
        // client pins X/Z to +-2.9999999E7). Strict '>' keeps a player standing on the hard clamp valid.
        if (flying && (Math.abs(rawX) > HARD_CODED_BORDER || Math.abs(rawZ) > HARD_CODED_BORDER || Math.abs(rawY) > Integer.MAX_VALUE)) {
            flagAndAlert();
            executeViolationSetback();
            event.setCancelled(true);
            player.onPacketCancel();
            lastWire = clamped;
            return;
        }

        // Landed on a destination the server teleported the player to (arena warp, /tp, setback). Recognised
        // by the destination itself - independent of the transaction-accept path, which a desync can defeat -
        // so a legit teleport is never read as a jump even when lastPacketWasTeleport stayed false.
        if (player.getSetbackTeleportUtil().wasRecentTeleportDestination(clamped.getX(), clamped.getY(), clamped.getZ())
                || player.getSetbackTeleportUtil().matchesPendingTeleport(clamped.getX(), clamped.getY(), clamped.getZ())) {
            lastWire = clamped;
            return;
        }

        if (lastWire == null) lastWire = new Vector3d(player.x, player.y, player.z);

        final double dx = clamped.getX() - lastWire.getX();
        final double dy = clamped.getY() - lastWire.getY();
        final double dz = clamped.getZ() - lastWire.getZ();

        if (dx * dx + dy * dy + dz * dz >= MovementCheckRunner.NON_PHYSICAL_TICK_DISTANCE_SQUARED) {
            if (debug) {
                LogUtil.info(String.format(
                        "[CrashA] %s jump=%.1f d=(%.1f,%.1f,%.1f) from=(%.1f,%.1f,%.1f) to=(%.1f,%.1f,%.1f) pendingTp=%d hasRot=%b %s",
                        player.getName(), Math.sqrt(dx * dx + dy * dy + dz * dz), dx, dy, dz,
                        lastWire.getX(), lastWire.getY(), lastWire.getZ(),
                        clamped.getX(), clamped.getY(), clamped.getZ(),
                        player.getSetbackTeleportUtil().pendingTeleports.size(), hasRotation,
                        flying ? "flying" : "vehicle"));
            }
            // Not a known server destination and a non-physical jump. Never flag - this is far more often a
            // teleport we could not attribute than a crash. Always cancel before the ~0.3ms prediction and
            // feed onPacketCancel's spam counter so a flooder that ignores our setback is still kicked. Send a
            // NEW setback only when one isn't already pending (don't spam). A cooperative player mid-setback is
            // caught on the destination gate above, not here, so cancelling during a pending setback only hits
            // the flooder - the setback is itself a server teleport, so the next honest packet lands on it.
            if (flying && !player.inVehicle() && !player.getSetbackTeleportUtil().shouldBlockMovement()) {
                executeViolationSetback();
            }
            event.setCancelled(true);
            player.onPacketCancel();
        }

        lastWire = clamped;
    }
}
