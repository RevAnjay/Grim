package ac.grim.grimac.manager.config;

import ac.grim.grimac.api.config.ConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Config view handed to {@code Check.onReload}. Resolves every key under the
 * {@code checks.} namespace (checks.yml) first, falling back to the bare key
 * (legacy config.yml). So a check reading {@code "Reach.threshold"} transparently
 * picks up {@code checks.Reach.threshold} when present, else the legacy value.
 */
public final class ChecksConfigView implements ConfigManager {

    private static final String PREFIX = "checks.";
    private final ConfigManager delegate;

    public ChecksConfigView(ConfigManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getStringElse(String key, String otherwise) {
        return delegate.getStringElse(PREFIX + key, delegate.getStringElse(key, otherwise));
    }

    @Override
    public @Nullable String getString(String key) {
        String v = delegate.getString(PREFIX + key);
        return v != null ? v : delegate.getString(key);
    }

    @Override
    public List<String> getStringList(String key) {
        List<String> v = delegate.getStringList(PREFIX + key);
        return v != null ? v : delegate.getStringList(key);
    }

    @Override
    public List<String> getStringListElse(String key, List<String> otherwise) {
        return delegate.getStringListElse(PREFIX + key, delegate.getStringListElse(key, otherwise));
    }

    @Override
    public int getIntElse(String key, int other) {
        return delegate.getIntElse(PREFIX + key, delegate.getIntElse(key, other));
    }

    @Override
    public long getLongElse(String key, long otherwise) {
        return delegate.getLongElse(PREFIX + key, delegate.getLongElse(key, otherwise));
    }

    @Override
    public double getDoubleElse(String key, double otherwise) {
        return delegate.getDoubleElse(PREFIX + key, delegate.getDoubleElse(key, otherwise));
    }

    @Override
    public boolean getBooleanElse(String key, boolean otherwise) {
        return delegate.getBooleanElse(PREFIX + key, delegate.getBooleanElse(key, otherwise));
    }

    @Override
    public <T> T get(String key) {
        T v = delegate.get(PREFIX + key);
        return v != null ? v : delegate.get(key);
    }

    @Override
    public <T> @Nullable T getElse(String key, T otherwise) {
        return delegate.getElse(PREFIX + key, delegate.getElse(key, otherwise));
    }

    @Override
    public <K, V> Map<K, V> getMap(String key) {
        Map<K, V> v = delegate.getMap(PREFIX + key);
        return v != null ? v : delegate.getMap(key);
    }

    @Override
    public @Nullable <K, V> Map<K, V> getMapElse(String key, Map<K, V> otherwise) {
        return delegate.getMapElse(PREFIX + key, delegate.getMapElse(key, otherwise));
    }

    @Override
    public @Nullable <T> List<T> getList(String path) {
        List<T> v = delegate.getList(PREFIX + path);
        return v != null ? v : delegate.getList(path);
    }

    @Override
    public @Nullable <T> List<T> getListElse(String path, List<T> otherwise) {
        return delegate.getListElse(PREFIX + path, delegate.getListElse(path, otherwise));
    }

    @Override
    public boolean hasLoaded() {
        return delegate.hasLoaded();
    }

    @Override
    public void reload() {
        delegate.reload();
    }
}
