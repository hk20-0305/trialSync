package com.trialsync.backend.controller;

import com.trialsync.backend.service.ReportService;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of {@code download_screening_report}.
 *
 * <p>The route lives on its own controller because its response is a byte stream rather than a JSON
 * body, but the path is unchanged - {@code report.pdf} is a literal suffix on the screening
 * resource, not a format parameter - and so are the content type and the attachment filename, both
 * of which the frontend depends on when it saves the file.
 */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** {@code GET /api/v1/screenings/{screening_id}/report.pdf}. */
    @GetMapping("/api/v1/screenings/{screeningId}/report.pdf")
    public ResponseEntity<byte[]> downloadScreeningReport(@PathVariable UUID screeningId) {
        byte[] content = reportService.renderScreeningReport(screeningId);
        String filename = "trialsync-screening-" + screeningId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
