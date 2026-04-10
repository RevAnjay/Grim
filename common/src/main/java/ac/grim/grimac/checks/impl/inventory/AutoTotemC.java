package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;

// Inspired by TotemGuard by Bram1903 (https://github.com/Bram1903/TotemGuard)
// Detects suspicious re-totem packet sequence after totem pop
@CheckData(name = "AutoTotemC", configName = "AutoTotem", description = "Checks for suspicious swap sequence after totem pop", decay = 0.025)
public class AutoTotemC extends Check implements PacketCheck {

    private int totemPopTransaction = -1;
    private boolean popConfirmed = false;
    private int swapCount = 0;
    private int flyingsSincePop = 0;
    private double buffer;
    private boolean debug = false;

    public AutoTotemC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        debug = config.getBooleanElse("AutoTotem.c.debug", false);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
            if (status.getStatus() == 35 && status.getEntityId() == player.entityID) {
                player.sendTransaction();
                totemPopTransaction = player.lastTransactionSent.get();
                popConfirmed = false;
                swapCount = 0;
                flyingsSincePop = 0;
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (totemPopTransaction != -1 && totemPopTransaction <= player.lastTransactionReceived.get()) {
            popConfirmed = true;
            totemPopTransaction = -1;
        }

        if (!popConfirmed) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())
                || event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            flyingsSincePop++;
            if (flyingsSincePop > 5) {
                popConfirmed = false;
            }
            return;
        }

        boolean isSwap = false;

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            if (click.getWindowId() == 0 && (
                    click.getWindowClickType() == WrapperPlayClientClickWindow.WindowClickType.SWAP
                    || click.getSlot() == 45)) {
                isSwap = true;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                isSwap = true;
            }
        }

        if (isSwap) {
            swapCount++;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            if (swapCount > 0 && flyingsSincePop == 0) {
                buffer++;

                if (debug) {
                    ac.grim.grimac.utils.anticheat.LogUtil.info("[AutoTotemC] " + player.user.getName()
                            + " pop+swap+close in same tick, swaps=" + swapCount + " buffer=" + buffer);
                }

                if (buffer > 2) {
                    flagAndAlert("swaps=" + swapCount);
                    buffer = 0;
                }
            } else {
                buffer = Math.max(0, buffer - 0.5);
                reward();
            }

            popConfirmed = false;
            swapCount = 0;
        }
    }
}
