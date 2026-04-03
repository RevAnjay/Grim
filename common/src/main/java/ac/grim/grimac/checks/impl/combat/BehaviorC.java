package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.utils.anticheat.LogUtil;
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

@CheckData(name = "BehaviorC", configName = "Behavior", description = "Checks for narrow attack interval spread", experimental = true)
public class BehaviorC extends Check implements PacketCheck {

    private final EvictingQueue<Long> intervals = new EvictingQueue<>(20);
    private long lastAttackTime = -1;
    private boolean playersOnly = true;
    private boolean debug = false;
    private long spreadThreshold = 60;
    private boolean mitigateHits = true;
    private int mitigationVL = 3;

    public BehaviorC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        playersOnly = config.getBooleanElse("Behavior.c.players-only", true);
        debug = config.getBooleanElse("Behavior.c.debug", false);
        spreadThreshold = config.getIntElse("Behavior.c.spread-threshold", 120);
        mitigateHits = config.getBooleanElse("Behavior.c.mitigate-hits", true);
        mitigationVL = config.getIntElse("Behavior.c.mitigation-vl", 3);
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

        if (mitigateHits && violations >= mitigationVL && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }

        long now = System.currentTimeMillis();

        if (lastAttackTime != -1) {
            long interval = now - lastAttackTime;

            if (interval < 50) {
                if (debug) {
                    LogUtil.info("[BehaviorC DEBUG] " + player.getName() + " " + "skip burst interval=" + interval + "ms");
                }
                return;
            }

            if (interval < 3000) {
                intervals.add(interval);

                if (intervals.size() >= 10) {
                    long min = Long.MAX_VALUE;
                    long max = Long.MIN_VALUE;
                    for (long i : intervals) {
                        if (i < min) min = i;
                        if (i > max) max = i;
                    }
                    long spread = max - min;

                    if (debug) {
                        LogUtil.info("[BehaviorC DEBUG] " + player.getName() + " " + "spread=" + spread + "ms min=" + min + " max=" + max
                                + " samples=" + intervals.size()
                                + " interval=" + interval + "ms");
                    } else if (spread < spreadThreshold) {
                        flagAndAlert("spread=" + spread + "ms");
                    }
                } else if (debug) {
                    LogUtil.info("[BehaviorC DEBUG] " + player.getName() + " " + "collecting samples=" + intervals.size() + "/10 interval=" + interval + "ms");
                }
            } else {
                intervals.clear();
                if (debug) {
                    LogUtil.info("[BehaviorC DEBUG] " + player.getName() + " " + "reset (pause > 3s)");
                }
            }
        }

        lastAttackTime = now;
    }
}
