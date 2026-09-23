package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Port of {@code trialsync.schemas.TrialUpdate} together with the {@code model_dump(exclude_unset=
 * True)} the PATCH endpoint applies to it.
 *
 * <p>This one cannot be a record. The Python endpoint distinguishes "the client omitted the key"
 * from "the client sent null", and only the keys actually present are copied onto the trial. A
 * record would collapse both cases to {@code null} and silently blank fields the caller never
 * mentioned, so each setter records that the key was seen. Jackson only invokes a setter for a key
 * that appears in the body, which is exactly the signal {@code exclude_unset} relies on.
 */
public class TrialUpdateRequest {

    @Size(min = 1, max = 64)
    private String registryId;

    @Size(min = 1, max = 240)
    private String title;

    @Size(min = 1, max = 160)
    private String condition;

    @Size(max = 40)
    private String phase;

    private boolean registryIdPresent;
    private boolean titlePresent;
    private boolean conditionPresent;
    private boolean phasePresent;

    public String getRegistryId() {
        return registryId;
    }

    @JsonProperty("registry_id")
    public void setRegistryId(String registryId) {
        this.registryId = registryId;
        this.registryIdPresent = true;
    }

    public String getTitle() {
        return title;
    }

    @JsonProperty("title")
    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String getCondition() {
        return condition;
    }

    @JsonProperty("condition")
    public void setCondition(String condition) {
        this.condition = condition;
        this.conditionPresent = true;
    }

    public String getPhase() {
        return phase;
    }

    @JsonProperty("phase")
    public void setPhase(String phase) {
        this.phase = phase;
        this.phasePresent = true;
    }

    public boolean isRegistryIdPresent() {
        return registryIdPresent;
    }

    public boolean isTitlePresent() {
        return titlePresent;
    }

    public boolean isConditionPresent() {
        return conditionPresent;
    }

    public boolean isPhasePresent() {
        return phasePresent;
    }
}
