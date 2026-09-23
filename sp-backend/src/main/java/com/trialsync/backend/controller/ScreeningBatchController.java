package com.trialsync.backend.controller;

import com.trialsync.backend.dto.screening.BatchCreateRequest;
import com.trialsync.backend.dto.screening.ScreeningBatchResponse;
import com.trialsync.backend.service.ScreeningBatchService;
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
 * Port of the batch routes of {@code trialsync.api.screenings}.
 *
 * <p>A batch screens every selected patient against every selected trial version through the same
 * service method the single-screening route uses, so batching changes throughput and nothing else:
 * the verdicts, the evidence and the stored rows are what the caller would have got one request at a
 * time.
 */
@RestController
@RequestMapping("/api/v1/screening-batches")
public class ScreeningBatchController {

    private final ScreeningBatchService batchService;

    public ScreeningBatchController(ScreeningBatchService batchService) {
        this.batchService = batchService;
    }

    /** {@code POST /api/v1/screening-batches} - runs the whole grid synchronously. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningBatchResponse createBatch(@Valid @RequestBody BatchCreateRequest payload) {
        UUID batchId = batchService.createBatch(payload);
        return batchService.getBatch(batchId);
    }

    /** {@code GET /api/v1/screening-batches} - the caller's newest 100 batches. */
    @GetMapping
    public List<ScreeningBatchResponse> listBatches() {
        return batchService.listBatches();
    }

    /** {@code GET /api/v1/screening-batches/{batch_id}}. */
    @GetMapping("/{batchId}")
    public ScreeningBatchResponse getBatch(@PathVariable UUID batchId) {
        return batchService.getBatch(batchId);
    }
}
