package com.example.opennms.service;

import com.example.opennms.config.BridgeProperties;
import com.example.opennms.dto.EnrichedNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for bridge status and management endpoints.
 */
@RestController
@RequestMapping("/api/v1/bridge")
public class BridgeController {

    private final ActiveAlarmCacheService activeAlarmCacheService;
    private final NodeCacheService nodeCacheService;
    private final AlertmanagerClientService alertmanagerClient;
    private final BridgeProperties properties;

    public BridgeController(ActiveAlarmCacheService activeAlarmCacheService,
                           NodeCacheService nodeCacheService,
                           AlertmanagerClientService alertmanagerClient,
                           BridgeProperties properties) {
        this.activeAlarmCacheService = activeAlarmCacheService;
        this.nodeCacheService = nodeCacheService;
        this.alertmanagerClient = alertmanagerClient;
        this.properties = properties;
    }

    /**
     * Get overall bridge status.
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BridgeStatus>> getStatus() {
        return alertmanagerClient.checkHealth()
                .map(alertmanagerHealthy -> {
                    BridgeStatus status = BridgeStatus.builder()
                            .timestamp(Instant.now())
                            .activeAlarms(activeAlarmCacheService.getActiveAlarmCount())
                            .cachedNodes(nodeCacheService.size())
                            .alertmanagerUrl(properties.getAlertmanager().getUrl())
                            .alertmanagerEnabled(properties.getAlertmanager().isEnabled())
                            .alertmanagerHealthy(alertmanagerHealthy)
                            .build();
                    return ResponseEntity.ok(status);
                });
    }

    /**
     * Get list of active alarms being tracked.
     */
    @GetMapping(value = "/alarms", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, AlarmSummary>> getActiveAlarms() {
        Map<String, AlarmSummary> summaries = activeAlarmCacheService.getActiveAlarms()
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> AlarmSummary.builder()
                                .alarmId(e.getValue().getAlarm().getId())
                                .uei(e.getValue().getAlarm().getUei())
                                .severity(e.getValue().getAlarm().getSeverity().name())
                                .nodeLabel(e.getValue().getAlarm().getNodeCriteria().getNodeLabel())
                                .lastSent(e.getValue().getLastSent())
                                .build()
                ));
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get list of cached nodes.
     */
    @GetMapping(value = "/nodes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Collection<NodeSummary>> getCachedNodes() {
        Collection<NodeSummary> summaries = nodeCacheService.getAllNodes().stream()
                .map(node -> NodeSummary.builder()
                        .id(node.getId())
                        .label(node.getLabel())
                        .foreignSource(node.getForeignSource())
                        .foreignId(node.getForeignId())
                        .location(node.getLocation())
                        .categories(node.getCategories())
                        .metadataCount(node.getFlatMetadata().size())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get details for a specific cached node.
     */
    @GetMapping(value = "/nodes/{nodeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrichedNode> getNode(@PathVariable long nodeId) {
        return nodeCacheService.getNodeById(nodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Manually trigger resend of all active alarms.
     */
    @PostMapping("/alarms/resend")
    public ResponseEntity<String> resendAlarms() {
        activeAlarmCacheService.resendActiveAlarms();
        return ResponseEntity.ok("Triggered resend of " + 
                activeAlarmCacheService.getActiveAlarmCount() + " active alarms");
    }

    /**
     * Clear all caches (useful for testing/debugging).
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearCaches() {
        int alarmCount = activeAlarmCacheService.getActiveAlarmCount();
        int nodeCount = nodeCacheService.size();
        
        activeAlarmCacheService.clear();
        nodeCacheService.clear();
        
        return ResponseEntity.ok("Cleared " + alarmCount + " alarms and " + nodeCount + " nodes from cache");
    }

    /**
     * Get Alertmanager status.
     */
    @GetMapping(value = "/alertmanager/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> getAlertmanagerStatus() {
        return alertmanagerClient.getStatus()
                .map(ResponseEntity::ok);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BridgeStatus {
        private Instant timestamp;
        private int activeAlarms;
        private int cachedNodes;
        private String alertmanagerUrl;
        private boolean alertmanagerEnabled;
        private boolean alertmanagerHealthy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmSummary {
        private long alarmId;
        private String uei;
        private String severity;
        private String nodeLabel;
        private Instant lastSent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeSummary {
        private long id;
        private String label;
        private String foreignSource;
        private String foreignId;
        private String location;
        private java.util.List<String> categories;
        private int metadataCount;
    }
}
