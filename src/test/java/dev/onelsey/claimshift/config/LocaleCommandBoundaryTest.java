package dev.onelsey.claimshift.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocaleCommandBoundaryTest {
    @Test
    void localeBundlesDoNotOwnExecutableClaimShiftSyntax() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        for (String locale : LocaleCatalog.SUPPORTED_LOCALES) {
            String path = "locales/messages-defaults/" + locale + ".yml";
            try (InputStream input = loader.getResourceAsStream(path)) {
                assertNotNull(input, "Missing locale resource: " + path);
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(text.contains("/claimshift"), "Localized command literal leaked into " + locale);
                assertFalse(text.contains("config|messages|both"), "Localized scope syntax leaked into " + locale);
            }
        }
    }
}
