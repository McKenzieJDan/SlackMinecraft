package com.mckenziejdan.slackminecraft;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PluginMetadataTest {
    @Test void packagedDescriptorDeclaresCommandPermissionAndVersion() throws Exception {
        try (var input = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(input);
            var description = new PluginDescriptionFile(input);
            assertTrue(description.getCommands().containsKey("slackminecraft"));
            assertEquals("slackminecraft.admin", description.getCommands().get("slackminecraft").get("permission"));
            assertTrue(description.getPermissions().stream().anyMatch(p -> p.getName().equals("slackminecraft.admin")));
            assertEquals("26.2", description.getAPIVersion());
            assertFalse(description.getVersion().contains("${"));
        }
    }
}
