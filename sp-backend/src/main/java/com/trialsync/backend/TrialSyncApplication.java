package com.trialsync.backend;

import com.trialsync.backend.config.TrialSyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Entry point for the TrialSync deterministic screening backend. */
@SpringBootApplication
@EnableConfigurationProperties(TrialSyncProperties.class)
public class TrialSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrialSyncApplication.class, args);
    }
}
//..\..\backend\.venv\Scripts\python.exe -m uvicorn service:app --host 0.0.0.0 --port 8001