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
        props.getUpload().setAllowedExtensions("jpg,pdf,txt,zip");
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
    void validate_rejectsUnsupportedExtension() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("virus.exe", 100));
        assertTrue(ex.getMessage().contains("不支持的文件类型"));
    }

    @Test
    void validate_acceptsAllowedExtension() {
        assertDoesNotThrow(() -> validator.validate("photo.jpg", 512));
    }

    @Test
    void validate_acceptsFileWithoutExtension() {
        assertDoesNotThrow(() -> validator.validate("README", 100));
    }
}
