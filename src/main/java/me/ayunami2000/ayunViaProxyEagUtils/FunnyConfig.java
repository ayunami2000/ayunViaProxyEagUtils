package me.ayunami2000.ayunViaProxyEagUtils;

import com.viaversion.viaversion.util.Config;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class FunnyConfig extends Config {
    public static boolean premiumSkins = false;
    public static boolean eaglerSkins = true;
    public static boolean eaglerVoice = true;
    public static int eaglerServerMode = 0;

    protected FunnyConfig(File configFile) {
        super(configFile, Logger.getLogger("FunnyConfig"));
    }

    @Override
    public URL getDefaultConfigURL() {
        return Main.class.getResource("/vpeagutils.yml");
    }

    @Override
    protected void handleConfig(Map<String, Object> map) {
        Object item = map.get("premium-skins");
        if (item instanceof Boolean) {
            premiumSkins = (Boolean) item;
        }
        item = map.get("eagler-skins");
        if (item instanceof Boolean) {
            eaglerSkins = (Boolean) item;
        }
        item = map.get("eagler-voice");
        if (item instanceof Boolean) {
            eaglerVoice = (Boolean) item;
        }
        item = map.get("eagler-server-mode");
        if (item instanceof Integer) {
            eaglerServerMode = (Integer) item;
        }
    }

    @Override
    public List<String> getUnsupportedOptions() {
        return Collections.emptyList();
    }
}
