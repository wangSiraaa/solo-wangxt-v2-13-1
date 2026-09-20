package com.tmhub.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Minimal TMX 1.4 parser: extracts translation units as (sourceLang, targetLang, source, target). */
@Component
public class TmxParser {

    public record Tu(String sourceLang, String targetLang, String source, String target) {}

    public List<Tu> parse(byte[] tmxBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(tmxBytes));
            List<Tu> tus = new ArrayList<>();
            NodeList tuNodes = doc.getElementsByTagName("tu");
            for (int i = 0; i < tuNodes.getLength(); i++) {
                Element tu = (Element) tuNodes.item(i);
                NodeList tuvs = tu.getElementsByTagName("tuv");
                if (tuvs.getLength() < 2) {
                    continue;
                }
                Element first = (Element) tuvs.item(0);
                Element second = (Element) tuvs.item(1);
                String lang1 = langOf(first);
                String lang2 = langOf(second);
                String seg1 = segText(first);
                String seg2 = segText(second);
                if (lang1 == null || lang2 == null || seg1 == null || seg2 == null) {
                    continue;
                }
                tus.add(new Tu(lang1, lang2, seg1, seg2));
            }
            return tus;
        } catch (Exception e) {
            throw new TmxParseException("Invalid TMX content: " + e.getMessage(), e);
        }
    }

    public List<Tu> parse(String tmx) {
        return parse(tmx.getBytes(StandardCharsets.UTF_8));
    }

    private String langOf(Element tuv) {
        String lang = tuv.getAttribute("xml:lang");
        if (lang == null || lang.isBlank()) {
            lang = tuv.getAttribute("lang");
        }
        return lang == null || lang.isBlank() ? null : lang.toLowerCase();
    }

    private String segText(Element tuv) {
        NodeList segs = tuv.getElementsByTagName("seg");
        if (segs.getLength() == 0) {
            return null;
        }
        return textContent(segs.item(0));
    }

    private String textContent(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                // Inline tags (e.g. <x/>, <ph/>) are placeholders; keep their serialized form.
                sb.append(serializeInline((Element) child));
            }
        }
        return sb.toString();
    }

    private String serializeInline(Element el) {
        StringBuilder sb = new StringBuilder("<").append(el.getTagName());
        var attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            sb.append(' ').append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append('"');
        }
        sb.append("/>");
        return sb.toString();
    }

    public static class TmxParseException extends RuntimeException {
        public TmxParseException(String message, Throwable cause) { super(message, cause); }
    }
}
