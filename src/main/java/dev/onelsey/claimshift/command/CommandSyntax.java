package dev.onelsey.claimshift.command;

import java.util.List;

/**
 * Executable command tokens are protocol, not localization content.
 * Keep these literals stable across every ClaimShift locale.
 */
public final class CommandSyntax {
    public static final String ROOT = "/claimshift";
    public static final String HELP = ROOT + " help";
    public static final String INFO = ROOT + " info";
    public static final String INSPECT = ROOT + " inspect";
    public static final String SYNC = ROOT + " sync";
    public static final String RELOAD = ROOT + " reload";
    public static final String LANGUAGE = ROOT + " language <locale> [config|messages|both]";
    public static final List<String> SCOPES = List.of("config", "messages", "both");

    private CommandSyntax() {
    }
}
