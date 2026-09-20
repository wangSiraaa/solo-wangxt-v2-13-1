package com.tmhub.service;

import com.tmhub.domain.TmEntry;

/** Deterministic TMX 1.4 writer. Same entries in the same order always produce the same bytes. */
public final class TmxWriter {

    private TmxWriter() {}

    public static String document(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<tmx version=\"1.4\">\n"
                + "  <header creationtool=\"tm-hub\" creationtoolversion=\"1.0\" segtype=\"sentence\"\n"
                + "          o-tmf=\"tm-hub\" adminlang=\"en\" datatype=\"xml\"/>\n"
                + "  <body>\n"
                + body
                + "  </body>\n"
                + "</tmx>\n";
    }

    public static String tu(TmEntry entry) {
        return "    <tu tuid=\"" + entry.getIdentityKey().substring(0, 16) + "\">\n"
                + "      <tuv xml:lang=\"" + escape(entry.getSourceLang()) + "\"><seg>"
                + escape(entry.getSourceText()) + "</seg></tuv>\n"
                + "      <tuv xml:lang=\"" + escape(entry.getTargetLang()) + "\"><seg>"
                + escape(entry.getTargetText()) + "</seg></tuv>\n"
                + "    </tu>\n";
    }

    public static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
