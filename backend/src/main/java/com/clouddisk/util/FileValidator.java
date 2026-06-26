package com.clouddisk.util;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FileValidator {

    private final CloudDiskProperties properties;
    private final Set<String> allowedExt;
    private final Set<String> blockedExt;
    private final boolean allowAll;

    public FileValidator(CloudDiskProperties properties) {
        this.properties = properties;
        this.allowedExt = parseSet(properties.getUpload().getAllowedExtensions());
        this.blockedExt = parseSet(properties.getUpload().getBlockedExtensions());
        this.allowAll = allowedExt.contains("*");
    }

    private static Set<String> parseSet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public void validate(String fileName, long size) {
        if (size <= 0) {
            throw new BusinessException("文件为空");
        }
        if (size > properties.getUpload().getMaxFileSize()) {
            throw new BusinessException("文件超过大小限制（最大 20GB）");
        }
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名无效");
        }
        String ext = FileTypeUtils.extensionOf(fileName);
        if (ext.isEmpty()) {
            return;
        }
        if (blockedExt.contains(ext)) {
            throw new BusinessException("不允许上传该类型文件: ." + ext);
        }
        if (allowAll || allowedExt.isEmpty()) {
            return;
        }
        if (!allowedExt.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext);
        }
    }

    /**
     * 头像上传：按文件内容识别图片类型，不强制后缀与魔数一致（避免 PNG 存为 .jpg 时报错）。
     */
    public void validateImageContent(java.io.InputStream in) {
        detectImageExtension(in);
    }

    /**
     * 识别图片魔数，返回规范扩展名：jpg / png / gif / webp。
     */
    public String detectImageExtension(java.io.InputStream in) {
        if (in == null) {
            throw new BusinessException("文件为空");
        }
        java.io.InputStream bis = in.markSupported() ? in : new java.io.BufferedInputStream(in);
        bis.mark(16);
        byte[] header = new byte[12];
        int read;
        try {
            read = bis.read(header, 0, 12);
            bis.reset();
        } catch (java.io.IOException e) {
            throw new BusinessException("无法读取文件内容");
        }
        if (read < 3) {
            throw new BusinessException("文件内容过短，不是合法的图片");
        }
        String hex = toHex(header, read);
        if (hex.startsWith("89504E47")) {
            return "png";
        }
        if (hex.startsWith("FFD8FF")) {
            return "jpg";
        }
        if (hex.startsWith("47494638")) {
            return "gif";
        }
        if (read >= 12 && hex.startsWith("52494646") && new String(header, 8, 4).equalsIgnoreCase("WEBP")) {
            return "webp";
        }
        throw new BusinessException("仅支持 JPG / PNG / GIF / WebP 图片");
    }

    private static String toHex(byte[] header, int read) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < read; i++) {
            sb.append(String.format("%02X", header[i]));
        }
        return sb.toString();
    }

    public void validateMagicBytes(String fileName, java.io.InputStream in) {
        if (in == null) {
            return;
        }
        String ext = FileTypeUtils.extensionOf(fileName).toLowerCase(Locale.ROOT);
        if (ext.isEmpty()) {
            return;
        }

        // 确保输入流支持 mark/reset，以便不破坏后续的流使用
        java.io.InputStream bis = in.markSupported() ? in : new java.io.BufferedInputStream(in);
        bis.mark(16);
        byte[] header = new byte[8];
        int read = 0;
        try {
            read = bis.read(header, 0, 8);
            bis.reset();
        } catch (java.io.IOException e) {
            return;
        }

        if (read <= 0) {
            return;
        }

        // 转十六进制
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < read; i++) {
            sb.append(String.format("%02X", header[i]));
        }
        String hex = sb.toString();

        // 1. 防范高危可执行文件（EXE/DLL: 4D5A, ELF: 7F454C46）
        if (hex.startsWith("4D5A")) {
            if (!"exe".equals(ext) && !"dll".equals(ext)) {
                throw new BusinessException("文件类型与内容不符：不允许将可执行程序伪装成其他格式上传", "SECURITY_VIOLATION");
            }
        }
        if (hex.startsWith("7F454C46")) {
            if (!"elf".equals(ext) && !"so".equals(ext) && !"bin".equals(ext)) {
                throw new BusinessException("文件类型与内容不符：不允许将二进制程序伪装成其他格式上传", "SECURITY_VIOLATION");
            }
        }

        // 2. 强类型后缀魔数精准校验
        if ("png".equals(ext) && !hex.startsWith("89504E47")) {
            throw new BusinessException("文件内容与后缀不符，不是合法的 PNG 图片");
        }
        if (("jpg".equals(ext) || "jpeg".equals(ext)) && !hex.startsWith("FFD8FF")) {
            throw new BusinessException("文件内容与后缀不符，不是合法的 JPEG 图片");
        }
        if ("gif".equals(ext) && !hex.startsWith("47494638")) {
            throw new BusinessException("文件内容与后缀不符，不是合法的 GIF 图片");
        }
        if ("pdf".equals(ext) && !hex.startsWith("25504446")) {
            throw new BusinessException("文件内容与后缀不符，不是合法的 PDF 文档");
        }
        if ("zip".equals(ext) && !hex.startsWith("504B0304")) {
            throw new BusinessException("文件内容与后缀不符，不是合法的 ZIP 压缩包");
        }

        // 3. 静态非脚本格式防 HTML/JS 脚本内容注入（防存储型 XSS）
        String contentHeader = new String(header, 0, Math.min(read, 8)).toLowerCase(Locale.ROOT);
        if (contentHeader.contains("<h") || contentHeader.contains("<s") || contentHeader.contains("<!") || contentHeader.contains("<?")) {
            try {
                bis.mark(256);
                byte[] largeHeader = new byte[256];
                int largeRead = bis.read(largeHeader, 0, 256);
                bis.reset();
                String checkStr = new String(largeHeader, 0, largeRead, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (checkStr.contains("<html") || checkStr.contains("<script") || checkStr.contains("javascript:") || checkStr.contains("onload=") || checkStr.contains("onerror=")) {
                    if (!"html".equals(ext) && !"htm".equals(ext) && !"xml".equals(ext) && !"txt".equals(ext) && !"md".equals(ext) && !"js".equals(ext)) {
                        throw new BusinessException("安全检查失败：检测到文件中包含 HTML/JavaScript 脚本内容，禁止上传");
                    }
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception ignored) {}
        }
    }
}
