package com.trialsync.backend.research.dropout.controller;

import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;
import com.trialsync.backend.research.dropout.service.ResearchDropoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing research endpoints for clinical dropout prediction.
 */
@RestController
@RequestMapping("/api/v1/research/dropout")
@Tag(name = "Research - Dropout", description = "Endpoints for research clinical dropout risk inference and SHAP explainability")
public class ResearchDropoutController {

    private final ResearchDropoutService dropoutService;

    public ResearchDropoutController(ResearchDropoutService dropoutService) {
        this.dropoutService = dropoutService;
    }

    @PostMapping("/predict")
    @Operation(summary = "Predict clinical dropout risk and compute SHAP explanation",
               description = "Delegates 33-feature pre-cutoff clinical vector to Python ML microservice via Spring WebClient")
    public ResponseEntity<DropoutPredictionResponse> predict(
            @Valid @RequestBody DropoutPredictionRequest request) {
        DropoutPredictionResponse response = dropoutService.predictDropout(request);
        return ResponseEntity.ok(response);
    }
}
