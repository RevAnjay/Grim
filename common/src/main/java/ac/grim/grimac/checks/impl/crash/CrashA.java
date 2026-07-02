package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.MovementCheckRunner;
import ac.grim.grimac.utils.math.VectorUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

@CheckData(name = "CrashA", stableKey = "grim.crash.large_position")
public class CrashA extends Check implements PacketCheck {
    private static final double HARD_CODED_BORDER = 2.9999999E7D;

    // The last position the client claimed on the wire, tracked across accepted, cancelled and exempt
    // packets. The gate measures each move against this rather than player.x (which freezes whenever a
    // packet is cancelled), so a legit burst of buffered movement reads as N small single-tick deltas
    // instead of one accumulated multi-tick jump. Netty-thread only.
    private Vector3d lastWire = null;

    public CrashA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.disableGrim) return;

        final PacketTypeCommon type = event.getPacketType();
        final boolean flying = WrapperPlayClientPlayerFlying.isFlying(type);
        final boolean vehicle = type == PacketType.Play.Client.VEHICLE_MOVE;
        if (!flying && !vehicle) return;

        final double rawX, rawY, rawZ;
        if (flying) {
            WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
            if (!packet.hasPositionChanged()) return; // rotation-only / onground-only carry no position
            rawX = packet.getLocation().getX();
            rawY = packet.getLocation().getY();
            rawZ = packet.getLocation().getZ();
        } else {
            Vector3d pos = new WrapperPlayClientVehicleMove(event).getPosition();
            rawX = pos.getX();
            rawY = pos.getY();
            rawZ = pos.getZ();
        }

        // Leave non-finite coords to CrashC and don't poison the wire reference with a NaN, which would
        // make every later delta NaN and silently disable the gate.
        if (!Double.isFinite(rawX) || !Double.isFinite(rawY) || !Double.isFinite(rawZ)) return;

        final Vector3d clamped = VectorUtils.clampVector(new Vector3d(rawX, rawY, rawZ));

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

        // A recognised server teleport (/tp, pearl, respawn, portal, setback-accept) re-seeds the wire
        // reference; lastPacketWasTeleport is populated only by the server teleport queues, never forgeable.
        if (player.packetStateData.lastPacketWasTeleport) {
            lastWire = clamped;
            return;
        }

        if (lastWire == null) lastWire = new Vector3d(player.x, player.y, player.z);

        final double dx = clamped.getX() - lastWire.getX();
        final double dy = clamped.getY() - lastWire.getY();
        final double dz = clamped.getZ() - lastWire.getZ();

        // Same non-physical single-tick line the prediction engine already trusts, applied one stage
        // earlier so we cancel before the ~0.3ms prediction runs instead of feeding it a garbage jump.
        if (dx * dx + dy * dy + dz * dz >= MovementCheckRunner.NON_PHYSICAL_TICK_DISTANCE_SQUARED) {
            if (flying && !player.inVehicle()) {
                // Rubber-band the player's own body. Flag only from a clean synced state (sampled before
                // our own setback) so a pending teleport/setback/unloaded chunk isn't punished as a crash.
                if (!player.getSetbackTeleportUtil().shouldBlockMovement()) flagAndAlert();
                executeViolationSetback();
            }
            // Vehicles are never setback here (that dismounts the rider); a plain cancel self-heals on the
            // next VEHICLE_MOVE. Either way the cancel feeds the packet-spam counter, which kicks a flood.
            event.setCancelled(true);
            player.onPacketCancel();
        }

        lastWire = clamped;
    }
}
