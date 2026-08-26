package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimGCD", configName = "AimGCD", description = "Detects rotations that do not follow Minecraft hardware sensitivity GCD step quantization", decay = 0.05)
public class AimGCD extends Check implements RotationListener, PacketReceiveListener {

    private static final Verbose V = Verbose.of("deltaX={f32}, deltaY={f32}, divX={f64}, divY={f64}");

    private float lastDeltaX = 0;
    private float lastDeltaY = 0;
    private double buffer = 0;
    private double maxBuffer = 5.0;
    private boolean cancelHits = true;
    private boolean recentAttack = false;
    private int ticksSinceAttack = 100;

    public AimGCD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxBuffer = config.getDoubleElse(getConfigName() + ".buffer", 5.0);
        cancelHits = config.getBooleanElse(getConfigName() + ".cancel-hits", true);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                recentAttack = true;
                ticksSinceAttack = 0;
            }
        }
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        ticksSinceAttack++;
        if (ticksSinceAttack > 5) {
            recentAttack = false;
        }

        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;
        if (player.inVehicle() || player.compensatedEntities.self.getRiding() != null) return;
        if (player.packetStateData.lastPacketWasTeleport) return;
        if (System.currentTimeMillis() - player.joinTime < 5000) return;

        float deltaX = rotationUpdate.getDeltaXRotABS();
        float deltaY = rotationUpdate.getDeltaYRotABS();

        // Cinematic camera / optifine smooth camera bypasses GCD cleanly by interpolation
        if (rotationUpdate.isCinematic()) {
            lastDeltaX = deltaX;
            lastDeltaY = deltaY;
            return;
        }

        // Only evaluate during combat engagement or significant deliberate rotation
        if (recentAttack && deltaX > 1.5f && deltaY > 0.5f && Math.abs(rotationUpdate.getTo().pitch()) < 89.0f) {
            AimProcessor processor = rotationUpdate.getProcessor();
            double divX = processor.divisorX;
            double divY = processor.divisorY;

            // In vanilla, rotation steps must resolve to a valid sensitivity divisor >= MINIMUM_DIVISOR
            // If delta is significant but divisor is below hardware minimum or modulo remainder is too large
            if (divX < GrimMath.MINIMUM_DIVISOR && divY < GrimMath.MINIMUM_DIVISOR) {
                if (++buffer > maxBuffer) {
                    if (flag(V.write(verbose()).f32(deltaX).f32(deltaY).f64(divX).f64(divY))) {
                        if (cancelHits && shouldModifyPackets()) {
                            player.cancelCombatTicks = 10;
                        }
                    }
                }
            } else {
                buffer = Math.max(0, buffer - 0.2);
            }
        } else {
            buffer = Math.max(0, buffer - 0.05);
        }

        lastDeltaX = deltaX;
        lastDeltaY = deltaY;
    }
}
