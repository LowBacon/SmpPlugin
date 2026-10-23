// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public record BuildInfo(String flavor, String serverApi) {

    public static final String PAPER = "paper";
    public static final String SPIGOT = "spigot";
    public static final String UNKNOWN = "unknown";

    private static final String RESOURCE = "smpplugin-build.properties";

    public static BuildInfo load(ClassLoader loader) {
        Properties properties = new Properties();
        try (InputStream input = loader.getResourceAsStream(RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Reported as unknown below.
        }
        String flavor = properties.getProperty("flavor", UNKNOWN).trim().toLowerCase(Locale.ROOT);
        if (!flavor.equals(PAPER) && !flavor.equals(SPIGOT)) {
            flavor = UNKNOWN;
        }
        return new BuildInfo(flavor, properties.getProperty("server.api", "unknown").trim());
    }

    public String mismatchWarning(ServerPlatform platform) {
        if (flavor.equals(SPIGOT) && platform.isPaperBased()) {
            return "This jar was built against the Spigot API but the server runs " + platform.displayName()
                    + ". Use the SmpPlugin-<version>-paper.jar build on Paper and Purpur.";
        }
        if (flavor.equals(PAPER) && (platform == ServerPlatform.SPIGOT || platform == ServerPlatform.CRAFTBUKKIT)) {
            return "This jar was built against the Paper API but the server runs " + platform.displayName()
                    + ". Use the SmpPlugin-<version>-spigot.jar build (mvn package -Pspigot).";
        }
        return null;
    }
}
