package com.example.opennms.service;

import com.example.opennms.config.BridgeProperties;
import com.example.opennms.dto.AlertmanagerAlert;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Client service for sending alerts to Prometheus Alertmanager.
 */
@Slf4j
@Service
public class AlertmanagerClientService {

    private final WebClient webClient;
    private final BridgeProperties properties;
    private final Counter alertsSentCounter;
    private final Counter alertsFailedCounter;
    private final Timer alertSendTimer;

    public AlertmanagerClientService(WebClient alertmanagerWebClient,
                                    BridgeProperties properties,
                                    MeterRegistry meterRegistry) {
        this.webClient = alertmanagerWebClient;
        this.properties = properties;
        
        this.alertsSentCounter = Counter.builder("opennms.bridge.alerts.sent")
                .description("Number of alerts successfully sent to Alertmanager")
                .register(meterRegistry);
        
        this.alertsFailedCounter = Counter.builder("opennms.bridge.alerts.failed")
                .description("Number of alerts that failed to send to Alertmanager")
                .register(meterRegistry);
        
        this.alertSendTimer = Timer.builder("opennms.bridge.alerts.send.time")
                .description("Time taken to send alerts to Alertmanager")
                .register(meterRegistry);
    }

    /**
     * Send a single alert to Alertmanager.
     */
    public Mono<Void> sendAlert(AlertmanagerAlert alert) {
        return sendAlerts(Collections.singletonList(alert));
    }

    /**
     * Send multiple alerts to Alertmanager in a batch.
     */
    public Mono<Void> sendAlerts(List<AlertmanagerAlert> alerts) {
        if (!properties.getAlertmanager().isEnabled()) {
            log.debug("Alertmanager forwarding is disabled, skipping {} alerts", alerts.size());
            return Mono.empty();
        }

        if (alerts.isEmpty()) {
            return Mono.empty();
        }

        log.debug("Sending {} alerts to Alertmanager at {}", 
                alerts.size(), properties.getAlertmanager().getUrl());

        return alertSendTimer.record(() ->
            webClient.post()
                .uri(properties.getAlertmanager().getApiPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(alerts)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> {
                    alertsSentCounter.increment(alerts.size());
                    log.debug("Successfully sent {} alerts to Alertmanager", alerts.size());
                })
                .doOnError(error -> {
                    alertsFailedCounter.increment(alerts.size());
                    log.error("Failed to send alerts to Alertmanager: {}", error.getMessage());
                })
                .retryWhen(Retry.backoff(
                        properties.getAlertmanager().getRetry().getMaxAttempts(),
                        properties.getAlertmanager().getRetry().getBackoff())
                    .filter(this::isRetryableError)
                    .doBeforeRetry(signal -> 
                        log.warn("Retrying alert send, attempt {}", signal.totalRetries() + 1)))
                .then()
        );
    }

    /**
     * Check if Alertmanager is healthy.
     */
    public Mono<Boolean> checkHealth() {
        return webClient.get()
                .uri("/api/v2/status")
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .onErrorReturn(false);
    }

    /**
     * Get Alertmanager status.
     */
    public Mono<String> getStatus() {
        return webClient.get()
                .uri("/api/v2/status")
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\": \"Unable to connect to Alertmanager\"}");
    }

    /**
     * Determine if an error should trigger a retry.
     */
    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            // Retry on 5xx errors or connection issues
            return ex.getStatusCode().is5xxServerError();
        }
        // Retry on connection errors
        return true;
    }
}
