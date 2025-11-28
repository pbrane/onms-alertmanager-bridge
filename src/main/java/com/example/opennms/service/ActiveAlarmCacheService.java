package com.example.opennms.service;

import com.example.opennms.config.BridgeProperties;
import com.example.opennms.dto.AlertmanagerAlert;
import com.example.opennms.model.OpennmsModelProtos;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for caching active alarms and periodically re-sending them to Alertmanager.
 * This is required because Alertmanager expects alerts to be continuously sent while active.
 */
@Slf4j
@Service
public class ActiveAlarmCacheService {

    private final ConcurrentHashMap<String, CachedAlarm> activeAlarms = new ConcurrentHashMap<>();
    private final AlertMapperService alertMapperService;
    private final AlertmanagerClientService alertmanagerClient;
    private final BridgeProperties properties;
    private final AtomicInteger activeAlarmCount;

    public ActiveAlarmCacheService(AlertMapperService alertMapperService,
                                   AlertmanagerClientService alertmanagerClient,
                                   BridgeProperties properties,
                                   MeterRegistry meterRegistry) {
        this.alertMapperService = alertMapperService;
        this.alertmanagerClient = alertmanagerClient;
        this.properties = properties;
        
        this.activeAlarmCount = new AtomicInteger(0);
        Gauge.builder("opennms.bridge.active.alarms", activeAlarmCount, AtomicInteger::get)
                .description("Number of active alarms being tracked")
                .register(meterRegistry);
    }

    /**
     * Add or update an alarm in the cache.
     */
    public void updateAlarm(OpennmsModelProtos.Alarm alarm) {
        String reductionKey = alarm.getReductionKey();
        
        // Check if this is a cleared/resolved alarm
        if (alarm.getSeverity() == OpennmsModelProtos.Severity.CLEARED ||
            alarm.getType() == OpennmsModelProtos.Alarm.Type.CLEAR) {
            resolveAlarm(reductionKey, alarm);
            return;
        }

        // Check if alarm should be forwarded
        if (!alertMapperService.shouldForwardAlarm(alarm)) {
            log.debug("Alarm {} filtered out by configuration", reductionKey);
            return;
        }

        // Convert to Alertmanager alert
        AlertmanagerAlert alert = alertMapperService.mapAlarmToAlert(alarm);

        // Cache and send
        CachedAlarm cachedAlarm = new CachedAlarm(alarm, alert, Instant.now());
        activeAlarms.put(reductionKey, cachedAlarm);
        activeAlarmCount.set(activeAlarms.size());

        log.debug("Cached active alarm: {} (severity={})", reductionKey, alarm.getSeverity());

        // Send immediately
        alertmanagerClient.sendAlert(alert)
                .subscribe(
                    null,
                    error -> log.error("Failed to send alarm {}: {}", reductionKey, error.getMessage()),
                    () -> log.debug("Sent alarm {} to Alertmanager", reductionKey)
                );
    }

    /**
     * Resolve/remove an alarm from the cache.
     */
    public void resolveAlarm(String reductionKey, OpennmsModelProtos.Alarm alarm) {
        CachedAlarm cached = activeAlarms.remove(reductionKey);
        activeAlarmCount.set(activeAlarms.size());

        if (cached != null) {
            log.debug("Resolving alarm: {}", reductionKey);
            
            // Send resolved alert
            AlertmanagerAlert resolvedAlert = alertMapperService.mapAlarmToAlert(alarm);
            resolvedAlert.setEndsAtNow();
            
            alertmanagerClient.sendAlert(resolvedAlert)
                    .subscribe(
                        null,
                        error -> log.error("Failed to send resolved alarm {}: {}", reductionKey, error.getMessage()),
                        () -> log.debug("Sent resolved alarm {} to Alertmanager", reductionKey)
                    );
        }
    }

    /**
     * Handle a tombstone (null value) for a deleted alarm.
     */
    public void handleDeletedAlarm(String reductionKey) {
        CachedAlarm cached = activeAlarms.remove(reductionKey);
        activeAlarmCount.set(activeAlarms.size());

        if (cached != null) {
            log.debug("Handling deleted alarm: {}", reductionKey);
            
            // Create and send a resolved alert
            AlertmanagerAlert resolvedAlert = cached.getAlert();
            resolvedAlert.setEndsAtNow();
            
            alertmanagerClient.sendAlert(resolvedAlert)
                    .subscribe(
                        null,
                        error -> log.error("Failed to send resolved alert for deleted alarm {}: {}", 
                                reductionKey, error.getMessage()),
                        () -> log.debug("Sent resolved alert for deleted alarm {}", reductionKey)
                    );
        }
    }

    /**
     * Periodically resend all active alarms to Alertmanager.
     * This runs based on the configured resend interval.
     */
    @Scheduled(fixedRateString = "${opennms.bridge.alert.resend-interval:60000}")
    public void resendActiveAlarms() {
        if (activeAlarms.isEmpty()) {
            return;
        }

        log.info("Resending {} active alarms to Alertmanager", activeAlarms.size());

        List<AlertmanagerAlert> alerts = new ArrayList<>();
        Instant now = Instant.now();

        for (Map.Entry<String, CachedAlarm> entry : activeAlarms.entrySet()) {
            CachedAlarm cached = entry.getValue();
            
            // Update the alert's startsAt to ensure proper timing
            AlertmanagerAlert alert = alertMapperService.mapAlarmToAlert(cached.getAlarm());
            alerts.add(alert);
            
            // Update last sent time
            cached.setLastSent(now);
        }

        if (!alerts.isEmpty()) {
            alertmanagerClient.sendAlerts(alerts)
                    .subscribe(
                        null,
                        error -> log.error("Failed to resend active alarms: {}", error.getMessage()),
                        () -> log.debug("Successfully resent {} active alarms", alerts.size())
                    );
        }
    }

    /**
     * Get the current count of active alarms.
     */
    public int getActiveAlarmCount() {
        return activeAlarms.size();
    }

    /**
     * Get all active alarms for debugging/status.
     */
    public Map<String, CachedAlarm> getActiveAlarms() {
        return new ConcurrentHashMap<>(activeAlarms);
    }

    /**
     * Clear all cached alarms.
     */
    public void clear() {
        activeAlarms.clear();
        activeAlarmCount.set(0);
    }

    /**
     * Inner class to hold cached alarm information.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CachedAlarm {
        private final OpennmsModelProtos.Alarm alarm;
        private final AlertmanagerAlert alert;
        private Instant lastSent;
    }
}
