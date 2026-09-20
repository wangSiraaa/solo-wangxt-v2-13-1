package com.acme.tm.service;

import com.acme.tm.error.ApiException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal TMX 1.x parser: extracts per-&lt;tu&gt; segments keyed by xml:lang. */
public final class TmxParser {
    private TmxParser() {}

    public record Tu(Map<String, String> segmentsByLang) {}

    public static List<Tu> parse(String tmx) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(tmx)));
            NodeList tus = doc.getElementsByTagName("tu");
            List<Tu> out = new ArrayList<>();
            for (int i = 0; i < tus.getLength(); i++) {
                Element tu = (Element) tus.item(i);
                NodeList tuvs = tu.getElementsByTagName("tuv");
                Map<String, String> byLang = new LinkedHashMap<>();
                for (int j = 0; j < tuvs.getLength(); j++) {
                    Element tuv = (Element) tuvs.item(j);
                    String lang = tuv.getAttribute("xml:lang");
                    if (lang == null || lang.isBlank()) lang = tuv.getAttribute("lang");
                    NodeList segs = tuv.getElementsByTagName("seg");
                    if (segs.getLength() > 0 && lang != null && !lang.isBlank()) {
                        byLang.put(lang.trim(), segs.item(0).getTextContent());
                    }
                }
                if (!byLang.isEmpty()) out.add(new Tu(byLang));
            }
            return out;
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid TMX payload: " + e.getMessage());
        }
    }
}
