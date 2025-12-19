package com.palacesky.controller.modules.logger;

import com.palacesky.controller.proxy.Proxy;

import java.io.File;

public class ProxyLogLoader {

    public static void load(LogService logService) {
        Proxy proxyPlugin = Proxy.getInstance();
        proxyPlugin.getServer().getEventManager().register(proxyPlugin, new ProxyLogListener(logService));
        proxyPlugin.getLogger().info("[LogModule] Listeners de Proxy registrados.");
    }

    public static File getDataFolder() {
        return new File("plugins/controller");
    }

    public static String getServerName() {
        return System.getProperty("controller.proxyId", "PROXY");
    }
}