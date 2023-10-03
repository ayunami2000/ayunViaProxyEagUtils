package me.ayunami2000.ayunViaProxyEagUtils;

import com.viaversion.viaversion.util.Config;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FunnyConfig extends Config {
    public static boolean premiumSkins = false;
    public static boolean eaglerSkins = true;
    public static boolean eaglerVoice = true;

    protected FunnyConfig(File configFile) {
        super(configFile);
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
    }

    @Override
    public List<String> getUnsupportedOptions() {
        return Collections.emptyList();
    }
}
