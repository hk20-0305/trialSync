package com.trialsync.backend;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("it")
public abstract class BaseIntegrationTest {

    static {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            envFile = new File("sp-backend/.env");
        }
        if (envFile.exists()) {
            try (FileInputStream fis = new FileInputStream(envFile)) {
                Properties props = new Properties();
                props.load(fis);
                props.forEach((k, v) -> {
                    if (System.getProperty(k.toString()) == null && System.getenv(k.toString()) == null) {
                        System.setProperty(k.toString(), v.toString());
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }
}
