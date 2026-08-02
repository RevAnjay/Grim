package ac.grim.grimac.platform.api;

import ac.grim.grimac.platform.api.sender.Sender;
import org.jetbrains.annotations.Nullable;

public interface PlatformServer {

    String getPlatformImplementationString();

    void dispatchCommand(Sender sender, String command);

    Sender getConsoleSender();

    void registerOutgoingPluginChannel(String name);

    double getTPS();

    // An objective's criterion never reaches clients, so only the platform can tell a health readout
    // from a kill counter.
    default @Nullable String getObjectiveCriterion(String name) {
        return null;
    }
}
