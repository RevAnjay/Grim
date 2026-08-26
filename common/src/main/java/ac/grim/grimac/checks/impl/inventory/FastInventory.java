package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.InventoryCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "FastInventory", configName = "FastInventory", description = "Detects impossible inventory looting and item transfer speeds", decay = 0.05)
public class FastInventory extends InventoryCheck {

    private long lastClickTime = 0;
    private int fastClickCount = 0;
    private double buffer = 0;
    private double maxBuffer = 4.0;
    private long minClickDelayMs = 40;
    private boolean cancel = true;

    public FastInventory(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBuffer = config.getDoubleElse(getConfigName() + ".buffer", 4.0);
        minClickDelayMs = config.getLongElse(getConfigName() + ".min-delay-ms", 40);
        cancel = config.getBooleanElse(getConfigName() + ".cancel", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        super.onPacketReceive(event);

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            WindowClickType clickType = click.getWindowClickType();

            // Ignore recipe crafting / drag quick crafts to prevent false positives
            if (clickType == WindowClickType.QUICK_CRAFT) {
                return;
            }

            // Only check container windows (windowId > 0 is an opened container)
            int windowId = click.getWindowId();
            if (windowId <= 0) return;

            long now = System.currentTimeMillis();
            if (lastClickTime > 0) {
                long delta = now - lastClickTime;

                if (delta < minClickDelayMs) {
                    fastClickCount++;
                    if (fastClickCount >= 3) {
                        if (++buffer > maxBuffer) {
                            if (flag(String.format("Fast container click delta=%dms < %dms count=%d", delta, minClickDelayMs, fastClickCount))) {
                                if (cancel && shouldModifyPackets()) {
                                    event.setCancelled(true);
                                    player.onPacketCancel();
                                    player.inventory.needResend = true;
                                }
                            }
                        }
                    }
                } else if (delta > 200) {
                    fastClickCount = 0;
                    buffer = Math.max(0, buffer - 0.25);
                }
            }
            lastClickTime = now;
        }
    }
}
