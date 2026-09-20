package com.acme.tm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder handling. A placeholder token is one of {n}, {{name}}, %s, %d, %1$s ...
 * optionally prefixed with a backslash escape. Replacements must preserve count, name,
 * order AND escape form exactly; anything else is an anomaly, never auto-guessed.
 */
public final class Placeholders {
    private Placeholders() {}

    private static final Pattern TOKEN = Pattern.compile(
            "(\\\\?)(\\{\\d+\\}|\\{\\{[A-Za-z0-9_.]+\\}\\}|%\\d+\\$?[sd]|%[sd])");

    /** Ordered placeholder tokens of a text, each including its escape form (e.g. "\\{0}"). */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            out.add(m.group(1) + m.group(2));
        }
        return out;
    }

    /** Canonical signature used for equality checks and storage. */
    public static String signature(String text) {
        return String.join(" ", tokens(text));
    }

    /** True iff source and target carry identical placeholder sequences (count, name, order, escape). */
    public static boolean preserved(String source, String target) {
        return tokens(source).equals(tokens(target));
    }

    /** Human-readable diff for the UI: tokens only in source vs only in target. */
    public static String diff(String source, String target) {
        List<String> s = new ArrayList<>(tokens(source));
        List<String> t = new ArrayList<>(tokens(target));
        List<String> missing = new ArrayList<>(s);
        List<String> added = new ArrayList<>(t);
        for (String tok : new ArrayList<>(t)) missing.remove(tok);
        for (String tok : new ArrayList<>(s)) added.remove(tok);
        if (missing.isEmpty() && added.isEmpty()) return "";
        return "missingInTarget=" + missing + " addedInTarget=" + added;
    }
}
