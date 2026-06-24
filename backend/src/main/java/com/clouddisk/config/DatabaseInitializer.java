package com.clouddisk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ensureColumn(metaData, "tb_team_space", "avatar",
                    "ALTER TABLE tb_team_space ADD COLUMN avatar VARCHAR(512) DEFAULT NULL COMMENT '团队头像路径'");
            jdbcTemplate.update(
                    "UPDATE tb_user SET role = 'SUPER_ADMIN' WHERE username = 'admin' AND role IN ('ADMIN', 'USER')");
            jdbcTemplate.update(
                    "UPDATE tb_user SET storage_quota = 536870912000 WHERE role = 'ADMIN' AND storage_quota = 214748364800");
        } catch (Exception e) {
            log.error("Failed to run database migrations", e);
        }
    }

    private void ensureColumn(DatabaseMetaData metaData, String table, String column, String ddl) throws Exception {
        boolean exists = false;
        try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
            if (rs.next()) {
                exists = true;
            }
        }
        if (!exists) {
            log.info("Database migration: Adding column '{}' to '{}'", column, table);
            jdbcTemplate.execute(ddl);
        }
    }
}
