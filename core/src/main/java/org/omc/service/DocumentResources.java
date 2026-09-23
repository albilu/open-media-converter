package org.omc.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Pattern;

/** Makes generated HTML independent of converter-owned image files. */
final class DocumentResources {
    private static final Pattern RESOURCE_ATTRIBUTE = Pattern.compile(
            "(?is)(\\b(?:src|background|data)\\s*=\\s*)([\"'])(.*?)\\2");
    private static final Pattern CSS_RESOURCE = Pattern.compile("(?is)url\\(\\s*([\"']?)(.*?)\\1\\s*\\)");

    private DocumentResources() {
    }

    static void embedHtml(Path html) throws IOException {
        String content = Files.readString(html);
        var attributes = RESOURCE_ATTRIBUTE.matcher(content);
        StringBuilder embedded = new StringBuilder();
        while (attributes.find()) {
            String replacement = attributes.group(1) + attributes.group(2)
                    + embedReference(html.getParent(), attributes.group(3)) + attributes.group(2);
            attributes.appendReplacement(embedded, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        attributes.appendTail(embedded);
        var css = CSS_RESOURCE.matcher(embedded);
        StringBuilder result = new StringBuilder();
        while (css.find()) {
            String replacement = "url(\"" + embedReference(html.getParent(), css.group(2)) + "\")";
            css.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        css.appendTail(result);
        Files.writeString(html, result);
    }

    private static String embedReference(Path directory, String reference) throws IOException {
        if (reference.isBlank() || reference.startsWith("#") || reference.startsWith("data:")
                || reference.startsWith("http:") || reference.startsWith("https:")) return reference;
        Path resource;
        try {
            resource = reference.startsWith("file:") ? Path.of(URI.create(reference))
                    : directory.resolve(URLDecoder.decode(reference.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid document resource: " + reference, e);
        }
        if (!Files.isRegularFile(resource) || !Files.isReadable(resource)) {
            throw new IOException("Document resource is missing: " + reference);
        }
        String type = Files.probeContentType(resource);
        if (type == null) type = "application/octet-stream";
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(resource));
    }
}
