package dev.onelsey.claimshift.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandSyntaxTest {
    @Test
    void commandTokensStayStableAndLocaleIndependent() {
        assertEquals("/claimshift help", CommandSyntax.HELP);
        assertEquals("/claimshift info", CommandSyntax.INFO);
        assertEquals("/claimshift inspect", CommandSyntax.INSPECT);
        assertEquals("/claimshift sync", CommandSyntax.SYNC);
        assertEquals("/claimshift reload", CommandSyntax.RELOAD);
        assertEquals("/claimshift language <locale> [config|messages|both]", CommandSyntax.LANGUAGE);
        assertEquals("/claimshift dryrun <on|off|status>", CommandSyntax.DRY_RUN);
        assertEquals("/claimshift dryrun off", CommandSyntax.DRY_RUN_OFF);
        assertEquals(java.util.List.of("config", "messages", "both"), CommandSyntax.SCOPES);
    }
}
