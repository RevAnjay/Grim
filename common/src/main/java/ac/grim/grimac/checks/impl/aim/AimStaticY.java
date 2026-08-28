package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockPlaceListener;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimStaticY", stableKey = "grim.aim.static_y", description = "Scanned Y rotation while looking horizontally.")
public class AimStaticY extends Check implements RotationListener, PacketReceiveListener, BlockPlaceListener {
    private static final Verbose V = Verbose.of("deltaX={f64}, deltaY={f64}");
    private double buffer = 0;
    private double decay;
    private int maxBuffer;
    private boolean cancelHits = true;
    private double minDeltaX, maxDeltaY;
    private double lastDeltaX;
    private int lastActionTick = -1;

    public AimStaticY(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        double deltaX = rotationUpdate.getDeltaXRotABS();
        double deltaY = rotationUpdate.getDeltaYRotABS();
        double deltaXAccel = deltaX - lastDeltaX;
        // Fix false positives in boats and other entities
        boolean isRiding = player.compensatedEntities.self.getRiding() != null;
        // In minecraft player cant move to [-90; 90], so it can cause false positives.
        boolean constantY = Math.abs(rotationUpdate.getTo().pitch()) == 90;
        // I am not sure that player will do it in the fight.
        boolean isAwkwardSituation = player.getLastTransactionReceived() - lastActionTick <= 20;
        boolean wasTeleported = player.packetStateData.lastPacketWasTeleport;
        boolean bigAccel = deltaXAccel > 10;

        lastDeltaX = deltaX;

        if (isRiding || constantY || bigAccel || !isAwkwardSituation || wasTeleported) {
            return;
        }

        if (deltaY <= maxDeltaY && deltaX >= minDeltaX) {
            if (++buffer > maxBuffer) {
                if (flag(V.write(verbose()).f64(deltaX).f64(deltaY))) {
                    if (cancelHits) player.cancelCombatTicks = 10;
                }
            }
        } else {
            buffer = Math.max(0, buffer - decay);

            if (buffer == 0) {
                reward();
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                lastActionTick = player.getLastTransactionReceived();
            }
        }
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        lastActionTick = player.getLastTransactionReceived();
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBuffer = config.getIntElse(getConfigName() + ".buffer", 7);
        decay = config.getDoubleElse(getConfigName() + ".decay", 1);
        minDeltaX = config.getDoubleElse(getConfigName() + ".minDeltaX", 1D);
        maxDeltaY = config.getDoubleElse(getConfigName() + ".maxDeltaY", 0.0001D);
        cancelHits = config.getBooleanElse(getConfigName() + ".cancel-hits", true);
    }
}
