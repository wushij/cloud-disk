package com.clouddisk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet rs = metaData.getColumns(null, null, "tb_team_space", "avatar")) {
                if (rs.next()) {
                    columnExists = true;
                }
            }
            if (!columnExists) {
                log.info("Database migration: Adding column 'avatar' to 'tb_team_space'");
                jdbcTemplate.execute("ALTER TABLE tb_team_space ADD COLUMN avatar VARCHAR(512) DEFAULT NULL COMMENT '团队头像路径'");
            }
        } catch (Exception e) {
            log.error("Failed to run database migrations", e);
        }
    }
}
