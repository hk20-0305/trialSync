package com.trialsync.backend.demo;

import com.trialsync.backend.TrialSyncApplication;
import com.trialsync.backend.service.DemoSeedService;
import com.trialsync.backend.service.DemoSeedService.DemoSeedSummary;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Component;

/**
 * CLI runner for seeding reproducible synthetic demo data in Spring Boot.
 *
 * <p>Triggered when application arguments include {@code seed-demo} or {@code --seed-demo}.
 */
@Component
public class DemoSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DemoSeedService demoSeedService;

    public DemoSeedRunner(DemoSeedService demoSeedService) {
        this.demoSeedService = demoSeedService;
    }

    @Override
    public void run(String... args) {
        boolean shouldSeed = Arrays.stream(args)
                .anyMatch(arg -> "seed-demo".equalsIgnoreCase(arg)
                        || "--seed-demo".equalsIgnoreCase(arg)
                        || "--trialsync.seed-demo".equalsIgnoreCase(arg));

        if (shouldSeed) {
            log.info("Starting Java demo dataset seeding...");
            DemoSeedSummary summary = demoSeedService.seedDemoData();
            String message = String.format(
                    "Seeded %d patients, %d trials, %d screenings (%d potentially eligible, %d likely ineligible, %d needs review), and %d chat messages for %s.",
                    summary.patients(),
                    summary.trials(),
                    summary.screenings(),
                    summary.potentiallyEligible(),
                    summary.likelyIneligible(),
                    summary.needsReview(),
                    summary.chatMessages(),
                    summary.email());
            log.info(message);
            System.out.println(message);
        }
    }

    public static void main(String[] args) {
        String[] runArgs = Arrays.copyOf(args, args.length + 1);
        runArgs[args.length] = "seed-demo";
        SpringApplication.run(TrialSyncApplication.class, runArgs);
    }
}
