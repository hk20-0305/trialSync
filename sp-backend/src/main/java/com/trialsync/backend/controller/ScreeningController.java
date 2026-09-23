package com.trialsync.backend.controller;

import com.trialsync.backend.dto.screening.ScreeningCreateRequest;
import com.trialsync.backend.dto.screening.ScreeningResponse;
import com.trialsync.backend.service.ScreeningService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the single-screening routes of {@code trialsync.api.screenings}.
 *
 * <p>The routes bind and delegate; the authorisation, the engine call and the transaction boundaries
 * all live in {@link ScreeningService}. A screening is created and then read back in a second
 * transaction, which is what Python's commit-then-re-read does and what makes the 201 body report
 * the stored evaluations rather than an in-memory guess at them.
 */
@RestController
@RequestMapping("/api/v1/screenings")
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    /** {@code POST /api/v1/screenings} - runs the engine once and stores the result. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningResponse createScreening(@Valid @RequestBody ScreeningCreateRequest payload) {
        UUID screeningId = screeningService.createScreening(payload);
        return screeningService.getScreening(screeningId);
    }

    /** {@code GET /api/v1/screenings} - the caller's newest 100 screenings. */
    @GetMapping
    public List<ScreeningResponse> listScreenings() {
        return screeningService.listScreenings();
    }

    /** {@code GET /api/v1/screenings/{screening_id}}. */
    @GetMapping("/{screeningId}")
    public ScreeningResponse getScreening(@PathVariable UUID screeningId) {
        return screeningService.getScreening(screeningId);
    }
}
