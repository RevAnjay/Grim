package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "CrystalAura", stableKey = "grim.combat.crystal_aura", description = "Detects impossible multi-action place and break cycles for End Crystals", decay = 0.05)
public class CrystalAura extends Check implements PacketReceiveListener {

    private int crystalPlacesInTick = 0;
    private int crystalAttacksInTick = 0;
    private double buffer = 0;
    private double maxBuffer = 4.0;
    private boolean cancelHits = true;

    public CrystalAura(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBuffer = config.getDoubleElse(getConfigName() + ".buffer", 4.0);
        cancelHits = config.getBooleanElse(getConfigName() + ".cancel-hits", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Reset counters on flying packet (new tick)
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            crystalPlacesInTick = 0;
            crystalAttacksInTick = 0;
            return;
        }

        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        if (System.currentTimeMillis() - player.joinTime < 5000) return;

        // 1. Detect Crystal placement on Obsidian / Bedrock
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(event);
            StateType state = player.compensatedWorld.getBlock(placement.getBlockPosition()).getType();
            if (state == StateTypes.OBSIDIAN || state == StateTypes.BEDROCK) {
                crystalPlacesInTick++;
            }
        }

        // 2. Detect Crystal attack
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                PacketEntity entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
                if (entity != null && entity.getType() == EntityTypes.END_CRYSTAL) {
                    crystalAttacksInTick++;

                    // Flag impossible rapid place-break cycles in a single tick without delay
                    if (crystalPlacesInTick >= 1 && crystalAttacksInTick >= 2 || crystalAttacksInTick >= 3) {
                        if (++buffer > maxBuffer) {
                            if (flag(String.format("places=%d attacks=%d in 1 tick", crystalPlacesInTick, crystalAttacksInTick))) {
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
}
