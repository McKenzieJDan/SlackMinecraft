package com.mckenziejdan.slackminecraft;

import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class PackagedPluginIT {
    @Test void bundledSlackClientSendsAndParsesHttpWithoutServerProvidedLibraries() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat.postMessage", exchange -> {
            try (exchange) {
                requestMethod.set(exchange.getRequestMethod());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = "{\"ok\":true,\"channel\":\"C123\",\"ts\":\"123.456\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        Path jar = Path.of(System.getProperty("pluginJar"));
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            String prefix = "com.mckenziejdan.slackminecraft.internal.slack.api.";
            Class<?> configType = loader.loadClass(prefix + "SlackConfig");
            Object config = configType.getConstructor().newInstance();
            configType.getMethod("setStatsEnabled", boolean.class).invoke(config, false);
            configType.getMethod("setMethodsEndpointUrlPrefix", String.class).invoke(config,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
            configType.getMethod("setHttpClientCallTimeoutMillis", Integer.class).invoke(config, 2000);
            Class<?> slackType = loader.loadClass(prefix + "Slack");
            Object slack = slackType.getMethod("getInstance", configType).invoke(null, config);
            try {
                Object methods = slackType.getMethod("methods", String.class).invoke(slack, "xoxb-test");
                Class<?> requestType = loader.loadClass(prefix + "methods.request.chat.ChatPostMessageRequest");
                Object builder = requestType.getMethod("builder").invoke(null);
                builder.getClass().getMethod("channel", String.class).invoke(builder, "C123");
                builder.getClass().getMethod("text", String.class).invoke(builder, "Hello & café");
                Object request = builder.getClass().getMethod("build").invoke(builder);
                Object response = loader.loadClass(prefix + "methods.MethodsClient")
                        .getMethod("chatPostMessage", requestType).invoke(methods, request);
                assertEquals(true, response.getClass().getMethod("isOk").invoke(response));
                assertEquals("C123", response.getClass().getMethod("getChannel").invoke(response));
                assertEquals("POST", requestMethod.get());
                assertEquals("Bearer xoxb-test", authorization.get());
                String form = java.net.URLDecoder.decode(requestBody.get(), StandardCharsets.UTF_8);
                assertTrue(form.contains("channel=C123"));
                assertTrue(form.contains("text=Hello & café"));
            } finally {
                ((AutoCloseable) slack).close();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test void bundledSlackClientLoadsWithoutServerProvidedLibraries() throws Exception {
        Path jar = Path.of(System.getProperty("pluginJar"));
        try (JarFile contents = new JarFile(jar.toFile())) {
            assertNotNull(contents.getEntry("plugin.yml"));
            try (var descriptor = contents.getInputStream(contents.getEntry("plugin.yml"))) {
                String yaml = new String(descriptor.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(yaml.contains("version: '" + System.getProperty("pluginVersion") + "'"));
            }
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
