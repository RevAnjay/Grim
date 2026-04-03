package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

// Detects automated sprint reset (W-tap/S-tap macros)
// A sprint reset right before every attack with consistent timing is inhuman
@CheckData(name = "BehaviorF", configName = "Behavior", description = "Checks for automated sprint resetting", experimental = true, decay = 0.025)
public class BehaviorF extends Check implements PacketCheck {

    private boolean sprintResetThisTick = false;
    private int consecutiveResets = 0;
    private boolean debug = false;
    private boolean playersOnly = true;

    public BehaviorF(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        debug = config.getBooleanElse("Behavior.f.debug", false);
        playersOnly = config.getBooleanElse("Behavior.f.players-only", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction(event);
            if (action.getAction() == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) {
                sprintResetThisTick = true;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

            if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
            if (player.inVehicle()) return;
            if (System.currentTimeMillis() - player.joinTime < 5000) return;

            if (playersOnly) {
                PacketEntity entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
                if (entity == null || entity.type != EntityTypes.PLAYER) return;
            }

            if (sprintResetThisTick) {
                consecutiveResets++;
                if (debug) {
                    LogUtil.info("[BehaviorF DEBUG] " + player.getName() + " sprint reset before attack, consecutive=" + consecutiveResets);
                }
                if (consecutiveResets >= 10) {
                    flagAndAlert("consecutive=" + consecutiveResets);
                }
            } else {
                if (consecutiveResets > 0 && debug) {
                    LogUtil.info("[BehaviorF DEBUG] " + player.getName() + " attack without reset, streak broken at " + consecutiveResets);
                }
                consecutiveResets = Math.max(0, consecutiveResets - 3);
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
            if (player.inVehicle()) return;

            if (sprintResetThisTick) {
                consecutiveResets++;
                if (consecutiveResets >= 10) {
                    flagAndAlert("consecutive=" + consecutiveResets);
                }
            } else {
                consecutiveResets = Math.max(0, consecutiveResets - 3);
            }
        }

        if (isTickPacket(event.getPacketType())) {
            sprintResetThisTick = false;
        }
    }
}
