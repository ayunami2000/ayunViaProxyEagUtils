package me.ayunami2000.ayunViaProxyEagUtils;

import com.viaversion.viaversion.util.Config;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FunnyConfig extends Config {
    private boolean premiumSkins = false;

    protected FunnyConfig(File configFile) {
        super(configFile);
    }

    @Override
    public URL getDefaultConfigURL() {
        return Main.class.getResource("/eaglerskins.yml");
    }

    @Override
    protected void handleConfig(Map<String, Object> map) {
        Object item = map.get("premium-skins");
        if (item instanceof Boolean) {
            this.premiumSkins = (Boolean) item;
        }
    }

    @Override
    public List<String> getUnsupportedOptions() {
        return Collections.emptyList();
    }

    public boolean getPremiumSkins() {
        return this.premiumSkins;
    }
}
