package com.eventflow.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 500)
    private String description;

    @Column(name = "secret_token", nullable = false)
    private String secretToken;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_types", columnDefinition = "text[]", nullable = false)
    private List<String> eventTypes = new ArrayList<>();

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, PAUSED, DISABLED

    @Column(name = "rate_limit", nullable = false)
    private int rateLimit = 100;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 5000;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WebhookEndpoint() {}

    public WebhookEndpoint(Organization organization, String url, String description,
                           String secretToken, List<String> eventTypes, int rateLimit, int timeoutMs) {
        this.organization = organization;
        this.url = url;
        this.description = description;
        this.secretToken = secretToken;
        this.eventTypes = eventTypes != null ? eventTypes : new ArrayList<>();
        this.rateLimit = rateLimit > 0 ? rateLimit : 100;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public boolean isSubscribedTo(String eventType) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return false;
        }
        return eventTypes.contains("*") || eventTypes.contains(eventType);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSecretToken() { return secretToken; }
    public void setSecretToken(String secretToken) { this.secretToken = secretToken; }

    public List<String> getEventTypes() { return eventTypes; }
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRateLimit() { return rateLimit; }
    public void setRateLimit(int rateLimit) { this.rateLimit = rateLimit; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
