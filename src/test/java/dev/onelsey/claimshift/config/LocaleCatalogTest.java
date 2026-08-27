package dev.onelsey.claimshift.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocaleCatalogTest {
    @Test
    void resolvesCanonicalAndCommonSpellings() {
        assertEquals("en_US", LocaleCatalog.canonicalize("en"));
        assertEquals("ru_RU", LocaleCatalog.canonicalize("ru-ru"));
        assertEquals("de_DE", LocaleCatalog.canonicalize("DE_de"));
        assertEquals("pt_BR", LocaleCatalog.canonicalize("pt"));
        assertEquals("zh_CN", LocaleCatalog.canonicalize("zh-cn"));
    }

    @Test
    void rejectsUnknownLocales() {
        assertNull(LocaleCatalog.canonicalize("xx_YY"));
        assertNull(LocaleCatalog.canonicalize(""));
        assertNull(LocaleCatalog.canonicalize(null));
    }
}
