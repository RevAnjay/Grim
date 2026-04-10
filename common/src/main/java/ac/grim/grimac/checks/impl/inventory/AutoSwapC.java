package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "AutoSwapC", configName = "AutoSwap", description = "Checks for isolated inventory swap without tick", decay = 0.05)
public class AutoSwapC extends Check implements PacketCheck {

    private int clickCountThisSession = 0;
    private int pendingSwapSlot = -1;
    private int flyingTicksInSession = 0;
    private long lastTickTimestamp = 0;
    private double buffer;
    private boolean debug = false;

    public AutoSwapC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        debug = config.getBooleanElse("AutoSwap.c.debug", false);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {}

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())
                || event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            lastTickTimestamp = System.currentTimeMillis();
            if (clickCountThisSession > 0) {
                flyingTicksInSession++;
            } else if (buffer > 0) {
                buffer = Math.max(0, buffer - 0.05);
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            clickCountThisSession++;

            if (click.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.SWAP
                    && click.getButton() == 40
                    && click.getWindowId() == 0) {

                if (clickCountThisSession == 1) {
                    pendingSwapSlot = click.getSlot();

                    if (violations > 0 && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                        player.inventory.needResend = true;
                    }
                } else {
                    pendingSwapSlot = -1;
                }
            } else {
                pendingSwapSlot = -1;
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            if (pendingSwapSlot != -1 && clickCountThisSession == 1 && flyingTicksInSession == 0) {
                boolean clientWasSkippingTicks = player.canSkipTicks()
                        && (System.currentTimeMillis() - lastTickTimestamp) > 80;

                buffer += clientWasSkippingTicks ? 0.5 : 1.0;

                if (buffer >= 4.0) {
                    if (debug) {
                        ac.grim.grimac.utils.anticheat.LogUtil.info("[AutoSwapC] " + player.user.getName()
                                + " isolated SWAP+CLOSE, slot=" + pendingSwapSlot
                                + " buffer=" + buffer + " skipping=" + clientWasSkippingTicks);
                    }

                    flagAndAlert("slot=" + pendingSwapSlot);
                }
            } else {
                buffer = Math.max(0, buffer - 0.5);
                reward();
            }

            clickCountThisSession = 0;
            pendingSwapSlot = -1;
            flyingTicksInSession = 0;
        }
    }
}
