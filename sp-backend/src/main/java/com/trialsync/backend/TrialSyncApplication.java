package com.trialsync.backend;

import com.trialsync.backend.config.TrialSyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Entry point for the TrialSync deterministic screening backend. */
@SpringBootApplication
@EnableConfigurationProperties(TrialSyncProperties.class)
public class TrialSyncApplication {

    public static void loadDotenv() {
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) {
            envFile = new java.io.File("sp-backend/.env");
        }
        if (envFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(envFile)) {
                java.util.Properties props = new java.util.Properties();
                props.load(fis);
                props.forEach((k, v) -> {
                    String key = k.toString();
                    String val = v.toString();
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        if ("DATABASE_URL".equals(key) && val.startsWith("postgres://")) {
                            val = "jdbc:postgresql://" + val.substring("postgres://".length());
                        }
                        System.setProperty(key, val);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(TrialSyncApplication.class, args);
    }
}
