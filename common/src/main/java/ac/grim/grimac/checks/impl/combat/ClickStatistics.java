package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "ClickStatistics", stableKey = "grim.combat.click_statistics", description = "Detects autoclickers via statistical click interval variance", decay = 0.05)
public class ClickStatistics extends Check implements PacketReceiveListener {

    private static final int SAMPLE_SIZE = 25;
    private final Deque<Long> clickIntervals = new ArrayDeque<>(SAMPLE_SIZE);
    private long lastClickTime = 0;
    private double buffer = 0;
    private double maxBuffer = 5.0;
    private double minStdDev = 8.0;
    private boolean cancelHits = true;

    public ClickStatistics(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBuffer = config.getDoubleElse(getConfigName() + ".buffer", 5.0);
        minStdDev = config.getDoubleElse(getConfigName() + ".min-std-dev", 8.0);
        cancelHits = config.getBooleanElse(getConfigName() + ".cancel-hits", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        if (System.currentTimeMillis() - player.joinTime < 5000) return;

        long now = System.currentTimeMillis();
        if (lastClickTime > 0) {
            long interval = now - lastClickTime;
            if (interval >= 25 && interval <= 300) {
                clickIntervals.addLast(interval);
                if (clickIntervals.size() > SAMPLE_SIZE) {
                    clickIntervals.removeFirst();
                }

                if (clickIntervals.size() >= SAMPLE_SIZE) {
                    double stdDev = calculateStdDev(clickIntervals);
                    if (stdDev < minStdDev) {
                        if (++buffer > maxBuffer) {
                            if (flag(String.format("Low click variance stdDev=%.2f < %.2f", stdDev, minStdDev))) {
                                if (cancelHits) player.cancelCombatTicks = 10;
                            }
                        }
                    } else {
                        buffer = Math.max(0, buffer - 0.25);
                    }
                }
            } else if (interval > 1000) {
                clickIntervals.clear();
            }
        }
        lastClickTime = now;
    }

    private double calculateStdDev(Iterable<Long> samples) {
        double sum = 0;
        int n = 0;
        for (long val : samples) {
            sum += val;
            n++;
        }
        if (n == 0) return 100.0;
        double mean = sum / n;

        double varianceSum = 0;
        for (long val : samples) {
            varianceSum += Math.pow(val - mean, 2);
        }
        return Math.sqrt(varianceSum / n);
    }
}
