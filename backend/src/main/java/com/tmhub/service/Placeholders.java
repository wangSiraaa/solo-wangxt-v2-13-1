package com.tmhub.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder and escape-sequence handling.
 *
 * Supported placeholder forms: {0}, {name}, {{name}}, %s, %1$s, %02d, &lt;x id="1"/&gt;-style tags.
 * Supported escape forms: \n \t \r \" \\ — their exact spelling must survive a replacement.
 *
 * A replacement is placeholder-safe only when source and target contain the same
 * placeholders (same names, same count, same order) and the same escape tokens.
 */
public final class Placeholders {

    private Placeholders() {}

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{[A-Za-z_][A-Za-z0-9_]*}}"          // {{name}}
                    + "|\\{[A-Za-z0-9_]+}"            // {0} or {name}
                    + "|%\\d+\\$[+#0-]*\\d*[a-zA-Z]"  // %1$s, %02d
                    + "|%[+#0-]*\\d*[sdif]"           // %s, %d
                    + "|<(x|ph)\\b[^>]*/>"            // <x id="1"/>, <ph .../>
    );

    private static final Pattern ESCAPE = Pattern.compile("\\\\[ntr\"\\\\]");

    public record PlaceholderDiff(List<String> sourceTokens, List<String> targetTokens,
                                  List<String> escapeSource, List<String> escapeTarget) {
        public boolean matches() {
            return sourceTokens.equals(targetTokens) && escapeSource.equals(escapeTarget);
        }
    }

    public static List<String> placeholdersOf(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    public static List<String> escapesOf(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = ESCAPE.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    public static PlaceholderDiff diff(String source, String target) {
        return new PlaceholderDiff(placeholdersOf(source), placeholdersOf(target),
                escapesOf(source), escapesOf(target));
    }
}
