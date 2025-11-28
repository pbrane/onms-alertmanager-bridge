package com.example.opennms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO representing a Prometheus Alertmanager alert.
 * Conforms to the Alertmanager API v2 specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertmanagerAlert {

    private static final DateTimeFormatter RFC3339_FORMATTER = 
            DateTimeFormatter.ISO_INSTANT;

    /**
     * Labels identify the alert and are used for deduplication.
     * Must include 'alertname'.
     */
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    /**
     * Annotations provide additional information about the alert.
     */
    @Builder.Default
    private Map<String, String> annotations = new HashMap<>();

    /**
     * Start time of the alert in RFC3339 format.
     */
    @JsonProperty("startsAt")
    private String startsAt;

    /**
     * End time of the alert in RFC3339 format.
     * If set, marks the alert as resolved.
     */
    @JsonProperty("endsAt")
    private String endsAt;

    /**
     * URL linking to the source of the alert.
     */
    @JsonProperty("generatorURL")
    private String generatorUrl;

    /**
     * Helper method to set startsAt from epoch milliseconds.
     */
    public void setStartsAtFromEpoch(long epochMillis) {
        this.startsAt = Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .format(RFC3339_FORMATTER);
    }

    /**
     * Helper method to set endsAt from epoch milliseconds.
     */
    public void setEndsAtFromEpoch(long epochMillis) {
        this.endsAt = Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .format(RFC3339_FORMATTER);
    }

    /**
     * Helper method to set endsAt to now (for resolving alerts).
     */
    public void setEndsAtNow() {
        this.endsAt = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(RFC3339_FORMATTER);
    }

    /**
     * Add a label to the alert.
     */
    public AlertmanagerAlert addLabel(String key, String value) {
        if (key != null && value != null && !value.isEmpty()) {
            // Sanitize label key for Prometheus compatibility
            String sanitizedKey = sanitizeLabelKey(key);
            this.labels.put(sanitizedKey, value);
        }
        return this;
    }

    /**
     * Add an annotation to the alert.
     */
    public AlertmanagerAlert addAnnotation(String key, String value) {
        if (key != null && value != null && !value.isEmpty()) {
            this.annotations.put(key, value);
        }
        return this;
    }

    /**
     * Sanitize label keys to comply with Prometheus label naming rules.
     * Label keys must match [a-zA-Z_][a-zA-Z0-9_]*
     */
    private String sanitizeLabelKey(String key) {
        if (key == null || key.isEmpty()) {
            return "unknown";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i == 0) {
                if (Character.isLetter(c) || c == '_') {
                    sb.append(c);
                } else if (Character.isDigit(c)) {
                    sb.append('_').append(c);
                } else {
                    sb.append('_');
                }
            } else {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
        }
        return sb.toString().toLowerCase();
    }
}
