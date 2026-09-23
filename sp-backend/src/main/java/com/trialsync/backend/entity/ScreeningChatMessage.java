package com.trialsync.backend.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Type;

import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One turn of the bounded conversation about a saved screening.
 *
 * <p>The table has {@code created_at} but no {@code updated_at}, and the API assigns
 * {@code created_at} explicitly so a user turn and the assistant turn answering it order
 * deterministically. CHECK constraints require {@code role} to be {@code user} or
 * {@code assistant}, and tie {@code answer_state} to the role: user turns must have none, assistant
 * turns must have one of {@code supported}, {@code insufficient_evidence} or {@code refused}.
 */
@Entity
@Table(name = "screening_chat_messages")
public class ScreeningChatMessage {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "screening_id", nullable = false, columnDefinition = "uuid")
    private UUID screeningId;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "answer_state", length = 32)
    private String answerState;

    @Type(JsonStringUserType.class)
    @Column(name = "citations_json", nullable = false, columnDefinition = "json")
    private String citationsJson = "[]";

    @Column(name = "provider", length = 40)
    private String provider;

    @Column(name = "model_id", length = 120)
    private String modelId;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", insertable = false, updatable = false)
    private Screening screening;

    protected ScreeningChatMessage() {
        // Required by JPA.
    }

    public ScreeningChatMessage(UUID screeningId, String role, String content) {
        this.screeningId = screeningId;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    void applyInsertTimestamp() {
        if (createdAt == null) {
            createdAt = TimestampedEntity.nowUtc();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public void setScreeningId(UUID screeningId) {
        this.screeningId = screeningId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAnswerState() {
        return answerState;
    }

    public void setAnswerState(String answerState) {
        this.answerState = answerState;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public void setCitationsJson(String citationsJson) {
        this.citationsJson = citationsJson;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Screening getScreening() {
        return screening;
    }
}
