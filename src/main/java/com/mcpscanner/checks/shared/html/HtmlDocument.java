package com.mcpscanner.checks.shared.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HtmlDocument {

    private static final Set<String> INERT_CONTAINERS = Set.of("template", "noscript");
    private static final String CONTENT_SECURITY_POLICY = "content-security-policy";

    private final Document document;

    private HtmlDocument(Document document) {
        this.document = document;
    }

    public static HtmlDocument parse(String html) {
        return new HtmlDocument(Jsoup.parse(html));
    }

    public boolean containsActiveElement(String tagName) {
        return anyActive(document.getElementsByTag(tagName.toLowerCase(Locale.ROOT)));
    }

    public boolean containsActiveAttribute(String attributeName) {
        return anyActive(document.getElementsByAttribute(attributeName.toLowerCase(Locale.ROOT)));
    }

    public List<String> metaContentSecurityPolicies() {
        return document.getElementsByTag("meta").stream()
                .filter(meta -> meta.parent() != null && "head".equals(meta.parent().normalName()))
                .filter(meta -> CONTENT_SECURITY_POLICY.equalsIgnoreCase(meta.attr("http-equiv").trim()))
                .map(meta -> meta.attr("content"))
                .toList();
    }

    private static boolean anyActive(Collection<Element> elements) {
        return elements.stream().anyMatch(HtmlDocument::isActive);
    }

    private static boolean isActive(Element element) {
        return element.parents().stream().noneMatch(parent -> INERT_CONTAINERS.contains(parent.normalName()));
    }
}
