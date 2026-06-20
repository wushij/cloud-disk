package com.clouddisk.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileTypeUtils {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "log", "csv", "json", "xml", "yaml", "yml", "ini", "properties",
            "html", "htm", "css", "js", "ts", "jsx", "tsx", "vue", "java", "py", "go", "rs",
            "c", "cpp", "h", "hpp", "cs", "sql", "sh", "bat", "conf", "cfg", "toml", "svg"
    );

    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "text/javascript"),
            Map.entry("ts", "text/typescript"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm")
    );

    private FileTypeUtils() {
    }

    public static String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String guessMimeType(String fileName) {
        String ext = extensionOf(fileName);
        if (ext.isEmpty()) {
            return "application/octet-stream";
        }
        return EXT_MIME.getOrDefault(ext, "application/octet-stream");
    }

    public static boolean isTextFile(String mimeType, String fileName) {
        String mime = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";
        if (mime.startsWith("text/")) {
            return true;
        }
        if (mime.contains("json") || mime.contains("xml") || mime.contains("javascript")) {
            return true;
        }
        return TEXT_EXTENSIONS.contains(extensionOf(fileName));
    }

    public static boolean isOfficeFile(String mimeType, String fileName) {
        String lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        return lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx")
                || lower.endsWith(".ppt") || lower.endsWith(".pptx");
    }
}
