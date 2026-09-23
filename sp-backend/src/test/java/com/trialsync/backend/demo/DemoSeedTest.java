package com.trialsync.backend.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trialsync.backend.BaseIntegrationTest;
import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.ScreeningChatMessage;
import com.trialsync.backend.entity.Trial;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.repository.PatientRepository;
import com.trialsync.backend.repository.ScreeningChatMessageRepository;
import com.trialsync.backend.repository.ScreeningRepository;
import com.trialsync.backend.repository.TrialRepository;
import com.trialsync.backend.repository.UserRepository;
import com.trialsync.backend.service.DemoSeedService;
import com.trialsync.backend.service.DemoSeedService.DemoSeedSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoSeedTest extends BaseIntegrationTest {

    @Autowired
    private DemoSeedService demoSeedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private TrialRepository trialRepository;

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private ScreeningChatMessageRepository chatMessageRepository;

    @Test
    void testDemoSeedIsReproducibleAndProducesExactMixedOutcomes() {
        // First run
        DemoSeedSummary first = demoSeedService.seedDemoData();
        assertEquals(6, first.patients());
        assertEquals(2, first.trials());
        assertEquals(12, first.screenings());
        assertEquals(4, first.potentiallyEligible());
        assertEquals(4, first.likelyIneligible());
        assertEquals(4, first.needsReview());
        assertEquals(8, first.chatMessages());

        User user = userRepository.findByEmail(DemoSeedService.DEMO_EMAIL).orElse(null);
        assertNotNull(user);
        UUID originalUserId = user.getId();

        // Check patients
        List<Patient> patients = patientRepository.findTop100ByOwnerIdOrderByUpdatedAtDesc(originalUserId);
        assertEquals(6, patients.size());
        Set<String> patientNames = patients.stream().map(Patient::getDisplayName).collect(Collectors.toSet());
        assertEquals(Set.of(
                "Synthetic Ada Mercer",
                "Synthetic Ben Carter",
                "Synthetic Cora Bennett",
                "Synthetic Dev Malik",
                "Synthetic Emi Tanaka",
                "Synthetic Finn Osei"
        ), patientNames);

        // Check trials
        List<Trial> trials = trialRepository.findTop100ByOwnerIdOrderByUpdatedAtDesc(originalUserId);
        assertEquals(2, trials.size());
        Set<String> registryIds = trials.stream().map(Trial::getRegistryId).collect(Collectors.toSet());
        assertEquals(Set.of("SYN-P8-METABOLIC", "SYN-P8-RENAL"), registryIds);

        // Check screenings and 4/4/4 distribution from database
        List<Screening> screenings = screeningRepository.findTop100ByOwnerIdOrderByCreatedAtDesc(originalUserId);
        assertEquals(12, screenings.size());
        Map<OverallState, Long> counts = screenings.stream()
                .collect(Collectors.groupingBy(Screening::getOverallState, Collectors.counting()));
        assertEquals(4L, counts.get(OverallState.POTENTIALLY_ELIGIBLE));
        assertEquals(4L, counts.get(OverallState.LIKELY_INELIGIBLE));
        assertEquals(4L, counts.get(OverallState.NEEDS_REVIEW));

        // Check chat messages
        List<ScreeningChatMessage> messages = chatMessageRepository.findAll().stream()
                .filter(m -> screenings.stream().anyMatch(s -> s.getId().equals(m.getScreeningId())))
                .toList();
        assertEquals(8, messages.size());

        Map<String, Long> answerStates = messages.stream()
                .filter(m -> m.getAnswerState() != null)
                .collect(Collectors.groupingBy(ScreeningChatMessage::getAnswerState, Collectors.counting()));
        assertEquals(2L, answerStates.get("supported"));
        assertEquals(1L, answerStates.get("refused"));
        assertEquals(1L, answerStates.get("insufficient_evidence"));

        // Second run to test idempotency and user UUID preservation
        DemoSeedSummary second = demoSeedService.seedDemoData();
        assertEquals(first, second);

        User userAfterSecond = userRepository.findByEmail(DemoSeedService.DEMO_EMAIL).orElse(null);
        assertNotNull(userAfterSecond);
        assertEquals(originalUserId, userAfterSecond.getId(), "Demo user UUID must be preserved across seed runs");

        List<Patient> patientsAfterSecond = patientRepository.findTop100ByOwnerIdOrderByUpdatedAtDesc(originalUserId);
        assertEquals(6, patientsAfterSecond.size(), "Patients must not be duplicated");

        List<Trial> trialsAfterSecond = trialRepository.findTop100ByOwnerIdOrderByUpdatedAtDesc(originalUserId);
        assertEquals(2, trialsAfterSecond.size(), "Trials must not be duplicated");

        List<Screening> screeningsAfterSecond = screeningRepository.findTop100ByOwnerIdOrderByCreatedAtDesc(originalUserId);
        assertEquals(12, screeningsAfterSecond.size(), "Screenings must not be duplicated");
    }
}
