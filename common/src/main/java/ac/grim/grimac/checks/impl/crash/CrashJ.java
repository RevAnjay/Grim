package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying.isFlying;

// The 1.17-1.20 Mojang duplicate position packet is isTickPacket=false, so it is exempt from the Timer
// throttle and never reaches the packet-spam counter. It also skips the prediction (handleFlying ignores
// its position), so a flood of them is cheap-but-unbounded. A legit client emits at most one per real
// tick; cap them at vanilla's own per-tick move ceiling (5) and let the surplus feed the spam kick.
@CheckData(name = "CrashJ", stableKey = "grim.crash.duplicate_flood")
public class CrashJ extends Check implements PacketCheck {
    private static final int MAX_DUPLICATES_BETWEEN_TICKS = 5;
    private int duplicates = 0; // netty-thread only

    public CrashJ(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        final PacketTypeCommon type = event.getPacketType();
        if (isFlying(type)) {
            if (player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                // No setCancelled: the duplicate has no authoritative position to drop, so counting it
                // toward the spam kick cannot desync a legit client.
                if (++duplicates > MAX_DUPLICATES_BETWEEN_TICKS) player.onPacketCancel();
            } else if (!player.packetStateData.lastPacketWasTeleport) {
                duplicates = 0; // a real movement packet marks a new tick boundary
            }
        } else if (type == PacketType.Play.Client.VEHICLE_MOVE
                || type == PacketType.Play.Client.CLIENT_TICK_END) {
            duplicates = 0;
        }
    }
}
