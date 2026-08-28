package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AnchorAura", stableKey = "grim.combat.anchor_aura", description = "Detects macro hotbar swap and explode sequence on Respawn Anchors", decay = 0.05)
public class AnchorAura extends Check implements PacketReceiveListener {

    private static final Verbose V = Verbose.of("swaps={uint}, uses={uint}");
    private int slotSwapsInTick = 0;
    private int anchorUsesInTick = 0;
    private double buffer = 0;
    private double maxBuffer = 3.0;
    private boolean cancelHits = true;

    public AnchorAura(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBuffer = config.getDoubleElse(getConfigName() + ".buffer", 3.0);
        cancelHits = config.getBooleanElse(getConfigName() + ".cancel-hits", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Reset on flying packet (new tick)
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            slotSwapsInTick = 0;
            anchorUsesInTick = 0;
            return;
        }

        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        if (System.currentTimeMillis() - player.joinTime < 5000) return;

        // 1. Detect fast hotbar slot swap
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            slotSwapsInTick++;
        }

        // 2. Detect Block Placement / Use on Respawn Anchor
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(event);
            StateType state = player.compensatedWorld.getBlock(placement.getBlockPosition()).getType();
            if (state == StateTypes.RESPAWN_ANCHOR) {
                anchorUsesInTick++;

                // Anchor macro pattern: 2+ slot swaps + 2+ anchor uses within the exact same tick
                if (slotSwapsInTick >= 2 && anchorUsesInTick >= 2 || anchorUsesInTick >= 3) {
                    if (++buffer > maxBuffer) {
                        if (flag(V.write(verbose()).uint(slotSwapsInTick).uint(anchorUsesInTick))) {
                            if (cancelHits) player.cancelCombatTicks = 10;
                        }
                    }
                } else {
                    buffer = Math.max(0, buffer - 0.1);
                }
            }
        }
    }
}
