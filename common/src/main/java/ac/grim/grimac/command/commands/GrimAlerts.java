package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.manager.AlertManagerImpl;
import ac.grim.grimac.manager.datastore.PlayerToggleStore;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GrimAlerts implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("alerts", Description.of("Toggle alerts for the sender"))
                        .permission("grim.alerts")
                        .optional("player", StringParser.stringParser(), arguments.onlinePlayerSuggestions())
                        .handler(this::handleAlerts)
        );
    }

    private void handleAlerts(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String targetFilter = context.getOrDefault("player", null);

        if (sender.isPlayer()) {
            PlatformPlayer player = Objects.requireNonNull(context.sender().getPlatformPlayer(), "player");
            AlertManagerImpl am = GrimAPI.INSTANCE.getAlertManager();
            boolean newState = !am.hasAlertsEnabled(player);
            am.setAlertsEnabled(player, newState, false);
            GrimAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore()
                    .applyUserToggle(player.getUniqueId(), PlayerToggleStore.KEY_ALERTS, newState);

            if (newState) {
                am.setPlayerFilter(player, targetFilter);
                if (targetFilter != null) {
                    String msg = GrimAPI.INSTANCE.getConfigManager().getConfig()
                            .getStringElse("alerts-filter", "%prefix% &fFiltering alerts for: &b%player%")
                            .replace("%player%", targetFilter);
                    sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
                }
            } else {
                am.setPlayerFilter(player, null);
            }
        } else if (sender.isConsole()) {
            GrimAPI.INSTANCE.getAlertManager().toggleConsoleAlerts();
        }
    }
}
