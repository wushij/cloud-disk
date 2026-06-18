package com.clouddisk.security;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * ClamAV clamd INSTREAM 扫描（profile / 配置启用时生效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clouddisk.virus-scan", name = "enabled", havingValue = "true")
public class ClamAvVirusScanService implements VirusScanService {

    private final CloudDiskProperties properties;

    @Override
    public void scan(InputStream inputStream, String fileName, long fileSize) {
        if (fileSize > properties.getVirusScan().getMaxBytes()) {
            log.warn("跳过大文件病毒扫描: {} ({} bytes)", fileName, fileSize);
            return;
        }
        try (Socket socket = new Socket()) {
            var cfg = properties.getVirusScan();
            socket.connect(new InetSocketAddress(cfg.getHost(), cfg.getPort()), cfg.getTimeoutMs());
            socket.setSoTimeout(cfg.getTimeoutMs());
            var out = socket.getOutputStream();
            var in = socket.getInputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > cfg.getMaxBytes()) {
                    writeChunk(out, new byte[0]);
                    log.warn("扫描中断（超出 maxBytes）: {}", fileName);
                    return;
                }
                writeChunk(out, buffer, 0, read);
            }
            writeChunk(out, new byte[0]);
            String response = readResponse(in);
            if (response != null && response.contains("FOUND")) {
                throw new BusinessException("文件未通过病毒扫描: " + fileName);
            }
            log.debug("ClamAV 扫描通过: {}", fileName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ClamAV 扫描失败: {}", e.getMessage());
            throw new BusinessException("病毒扫描服务不可用，请稍后重试");
        }
    }

    private void writeChunk(java.io.OutputStream out, byte[] data) throws java.io.IOException {
        writeChunk(out, data, 0, data.length);
    }

    private void writeChunk(java.io.OutputStream out, byte[] data, int offset, int len) throws java.io.IOException {
        ByteBuffer sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(len);
        out.write(sizeBuf.array());
        if (len > 0) {
            out.write(data, offset, len);
        }
    }

    private String readResponse(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (buf.size() > 4096) break;
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }
}
