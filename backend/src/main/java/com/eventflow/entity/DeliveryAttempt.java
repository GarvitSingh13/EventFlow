package com.eventflow.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private EventDelivery delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_headers", columnDefinition = "text")
    private String responseHeaders;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt = Instant.now();

    public DeliveryAttempt() {}

    public DeliveryAttempt(EventDelivery delivery, int attemptNumber, Integer responseStatusCode,
                           String responseHeaders, String responseBody, Integer executionTimeMs,
                           String errorMessage) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.responseStatusCode = responseStatusCode;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.executedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public EventDelivery getDelivery() { return delivery; }
    public void setDelivery(EventDelivery delivery) { this.delivery = delivery; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public Integer getResponseStatusCode() { return responseStatusCode; }
    public void setResponseStatusCode(Integer responseStatusCode) { this.responseStatusCode = responseStatusCode; }

    public String getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(String responseHeaders) { this.responseHeaders = responseHeaders; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public Integer getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Integer executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
