package com.mckenziejdan.slackminecraft;

import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class PackagedPluginIT {
    @Test void bundledSlackClientLoadsWithoutServerProvidedLibraries() throws Exception {
        Path jar = Path.of("target/SlackMinecraft-2.0.0-SNAPSHOT.jar");
        try (JarFile contents = new JarFile(jar.toFile())) {
            assertNotNull(contents.getEntry("plugin.yml"));
            assertNull(contents.getEntry("org/bukkit/Bukkit.class"));
            assertNull(contents.getEntry("com/slack/api/Slack.class"));
            assertNotNull(contents.getEntry("META-INF/services/com.mckenziejdan.slackminecraft.internal.slf4j.spi.SLF4JServiceProvider"));
        }
        // The platform parent deliberately excludes all Maven test dependencies.
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            String prefix = "com.mckenziejdan.slackminecraft.internal.";
            Class<?> configType = loader.loadClass(prefix + "slack.api.SlackConfig");
            Object config = configType.getConstructor().newInstance();
            configType.getMethod("setStatsEnabled", boolean.class).invoke(config, false);
            Class<?> slackType = loader.loadClass(prefix + "slack.api.Slack");
            Object slack = slackType.getMethod("getInstance", configType).invoke(null, config);
            try {
                assertNotNull(slackType.getMethod("methods", String.class).invoke(slack, "xoxb-test"));
                assertNotNull(loader.loadClass(prefix + "websocket.client.WebSocketClient"));
                Class<?> loggerFactory = loader.loadClass(prefix + "slf4j.LoggerFactory");
                Object logger = loggerFactory.getMethod("getLogger", String.class).invoke(null, "packaging-test");
                assertTrue(logger.getClass().getName().contains("JDK14LoggerAdapter"));
            } finally {
                ((AutoCloseable) slack).close();
            }
        }
    }
}
