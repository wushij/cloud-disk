package com.clouddisk.util;

import com.clouddisk.common.BusinessException;
import com.clouddisk.config.CloudDiskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        CloudDiskProperties props = new CloudDiskProperties();
        props.getUpload().setAllowedExtensions("*");
        props.getUpload().setBlockedExtensions("exe,bat");
        props.getUpload().setMaxFileSize(1024);
        validator = new FileValidator(props);
    }

    @Test
    void validate_rejectsEmptyFile() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("a.txt", 0));
        assertEquals("文件为空", ex.getMessage());
    }

    @Test
    void validate_rejectsOversizedFile() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("a.txt", 2048));
        assertTrue(ex.getMessage().contains("大小限制"));
    }

    @Test
    void validate_rejectsBlockedExtension() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("virus.exe", 100));
        assertTrue(ex.getMessage().contains("不允许上传"));
    }

    @Test
    void validate_acceptsCommonTextFile() {
        assertDoesNotThrow(() -> validator.validate("notes.txt", 512));
    }

    @Test
    void validate_acceptsFileWithoutExtension() {
        assertDoesNotThrow(() -> validator.validate("README", 100));
    }

    @Test
    void validateMagicBytes_acceptsPngContentWithJpgExtension() throws Exception {
        // PNG magic bytes, .jpg extension — common with wallpaper downloads
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
        assertDoesNotThrow(() -> validator.validateMagicBytes("wallpaper.jpg",
                new java.io.ByteArrayInputStream(pngHeader)));
    }

    @Test
    void validateMagicBytes_rejectsNonImageWithJpgExtension() {
        byte[] fake = "not-an-image".getBytes();
        assertThrows(BusinessException.class, () -> validator.validateMagicBytes("fake.jpg",
                new java.io.ByteArrayInputStream(fake)));
    }
}
