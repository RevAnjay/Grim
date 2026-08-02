package ac.grim.grimac.platform.bukkit;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.platform.api.Platform;
import ac.grim.grimac.platform.api.PlatformServer;
import ac.grim.grimac.platform.api.sender.Sender;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.ScoreboardManager;

public class BukkitPlatformServer implements PlatformServer {

    @Override
    public String getPlatformImplementationString() {
        return Bukkit.getVersion();
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getObjectiveCriterion(String name) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return null;
        Objective objective = manager.getMainScoreboard().getObjective(name);
        return objective == null ? null : objective.getCriteria();
    }

    @Override
    public void dispatchCommand(Sender sender, String command) {
        CommandSender commandSender = GrimACBukkitLoaderPlugin.LOADER.getBukkitSenderFactory().reverse(sender);
        Bukkit.dispatchCommand(commandSender, command);
    }

    @Override
    public Sender getConsoleSender() {
        return GrimACBukkitLoaderPlugin.LOADER.getBukkitSenderFactory().map(Bukkit.getConsoleSender());
    }

    @Override
    public void registerOutgoingPluginChannel(String name) {
        GrimACBukkitLoaderPlugin.LOADER.getServer().getMessenger().registerOutgoingPluginChannel(GrimACBukkitLoaderPlugin.LOADER, name);
    }

    @Override
    public double getTPS() {
        // Folia throws UnsupportedOperationException on calling getTPS(), there is no API for getting TPS on Folia
        if (GrimAPI.INSTANCE.getPlatform() == Platform.FOLIA) {
            return Double.NaN;
        }
        return SpigotReflectionUtil.getTPS();
    }
}
