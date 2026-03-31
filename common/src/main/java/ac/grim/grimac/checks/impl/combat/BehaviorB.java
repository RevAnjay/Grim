package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.lists.EvictingQueue;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "BehaviorB", configName = "Behavior", description = "Checks for consistent attack timing", experimental = true)
public class BehaviorB extends Check implements PacketCheck {

    private final EvictingQueue<Long> intervals = new EvictingQueue<>(20);
    private long lastAttackTime = -1;
    private boolean playersOnly = true;
    private boolean debug = false;

    public BehaviorB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        playersOnly = config.getBooleanElse("Behavior.b.players-only", true);
        debug = config.getBooleanElse("Behavior.b.debug", false);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        if (player.inVehicle()) return;
        if (System.currentTimeMillis() - player.joinTime < 5000) return;

        if (playersOnly) {
            PacketEntity entity = player.compensatedEntities.entityMap.get(interact.getEntityId());
            if (entity == null || entity.type != EntityTypes.PLAYER) return;
        }

        long now = System.currentTimeMillis();

        if (lastAttackTime != -1) {
            long interval = now - lastAttackTime;

            if (interval < 50) {
                if (debug) {
                    LogUtil.info("[BehaviorB DEBUG] " + player.getName() + " " + "skip burst interval=" + interval + "ms");
                }
                return;
            }

            if (interval < 3000) {
                intervals.add(interval);

                if (intervals.size() >= 10) {
                    double stdDev = GrimMath.stdDev(intervals);
                    double mean = GrimMath.mean(intervals);

                    if (debug) {
                        LogUtil.info("[BehaviorB DEBUG] " + player.getName() + " " + "stdDev=" + String.format("%.2f", stdDev)
                                + " mean=" + String.format("%.1f", mean)
                                + "ms samples=" + intervals.size()
                                + " interval=" + interval + "ms");
                    } else if (stdDev < 15.0) {
                        flagAndAlert("stdDev=" + String.format("%.2f", stdDev));
                    }
                } else if (debug) {
                    LogUtil.info("[BehaviorB DEBUG] " + player.getName() + " " + "collecting samples=" + intervals.size() + "/10 interval=" + interval + "ms");
                }
            } else {
                intervals.clear();
                if (debug) {
                    LogUtil.info("[BehaviorB DEBUG] " + player.getName() + " " + "reset (pause > 3s)");
                }
            }
        }

        lastAttackTime = now;
    }

}
