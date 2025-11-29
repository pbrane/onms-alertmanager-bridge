package com.beaconstrategists.onms.service;

import com.beaconstrategists.onms.config.BridgeProperties;
import com.beaconstrategists.onms.dto.AlertmanagerAlert;
import com.beaconstrategists.onms.dto.EnrichedNode;
import com.beaconstrategists.onms.model.OpennmsModelProtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service for mapping OpenNMS alarms to Prometheus Alertmanager alerts.
 */
@Slf4j
@Service
public class AlertMapperService {

    private final BridgeProperties properties;
    private final NodeCacheService nodeCacheService;
    private final ObjectMapper objectMapper;

    public AlertMapperService(BridgeProperties properties,
                             NodeCacheService nodeCacheService,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.nodeCacheService = nodeCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * Convert an OpenNMS alarm to an Alertmanager alert.
     */
    public AlertmanagerAlert mapAlarmToAlert(OpennmsModelProtos.Alarm alarm) {
        AlertmanagerAlert alert = AlertmanagerAlert.builder().build();

        // Set required alertname label from UEI
        String alertName = buildAlertName(alarm.getUei());
        alert.addLabel("alertname", alertName);

        // Add OpenNMS-specific labels
        alert.addLabel("opennms_alarm_id", String.valueOf(alarm.getId()));
        alert.addLabel("opennms_reduction_key", alarm.getReductionKey());
        alert.addLabel("severity", mapSeverity(alarm.getSeverity()));
        alert.addLabel("opennms_alarm_type", alarm.getType().name());

        // Add service name if present
        if (!alarm.getServiceName().isEmpty()) {
            alert.addLabel("service", alarm.getServiceName());
        }

        // Add IP address if present
        if (!alarm.getIpAddress().isEmpty()) {
            alert.addLabel("instance", alarm.getIpAddress());
            alert.addLabel("ip_address", alarm.getIpAddress());
        }

        // Add interface index if present
        if (alarm.getIfIndex() > 0) {
            alert.addLabel("if_index", String.valueOf(alarm.getIfIndex()));
        }

        // Add trouble ticket info if present
        if (!alarm.getTroubleTicketId().isEmpty()) {
            alert.addLabel("trouble_ticket_id", alarm.getTroubleTicketId());
            alert.addLabel("trouble_ticket_state", alarm.getTroubleTicketState().name());
        }

        // Add managed object info if present
        if (!alarm.getManagedObjectType().isEmpty()) {
            alert.addLabel("managed_object_type", alarm.getManagedObjectType());
        }
        if (!alarm.getManagedObjectInstance().isEmpty()) {
            alert.addLabel("managed_object_instance", alarm.getManagedObjectInstance());
        }

        // Add node-related labels
        addNodeLabels(alert, alarm);

        // Add static labels from configuration
        properties.getAlert().getStaticLabels().forEach(alert::addLabel);

        // Set timestamps
        if (alarm.getFirstEventTime() > 0) {
            alert.setStartsAtFromEpoch(alarm.getFirstEventTime());
        }

        // Handle resolved/cleared alarms
        if (alarm.getSeverity() == OpennmsModelProtos.Severity.CLEARED ||
            alarm.getType() == OpennmsModelProtos.Alarm.Type.CLEAR) {
            alert.setEndsAtNow();
        }

        // Set generator URL to link back to OpenNMS
        String generatorUrl = String.format("%s/alarm/detail.htm?id=%d",
                properties.getOpennms().getBaseUrl(), alarm.getId());
        alert.setGeneratorUrl(generatorUrl);

        // Add annotations
        addAnnotations(alert, alarm);

        return alert;
    }

    /**
     * Create a resolved alert for a deleted alarm.
     */
    public AlertmanagerAlert createResolvedAlert(String reductionKey) {
        AlertmanagerAlert alert = AlertmanagerAlert.builder().build();
        
        alert.addLabel("alertname", "opennms_alarm_deleted");
        alert.addLabel("opennms_reduction_key", reductionKey);
        alert.setEndsAtNow();
        
        return alert;
    }

    /**
     * Add node-related labels to the alert.
     */
    private void addNodeLabels(AlertmanagerAlert alert, OpennmsModelProtos.Alarm alarm) {
        OpennmsModelProtos.NodeCriteria nodeCriteria = alarm.getNodeCriteria();
        
        if (nodeCriteria == null || nodeCriteria.getId() == 0) {
            return;
        }

        // Add basic node info from alarm's node criteria
        alert.addLabel("node_id", String.valueOf(nodeCriteria.getId()));
        
        if (!nodeCriteria.getNodeLabel().isEmpty()) {
            alert.addLabel("node_label", nodeCriteria.getNodeLabel());
        }
        
        if (!nodeCriteria.getForeignSource().isEmpty()) {
            alert.addLabel("foreign_source", nodeCriteria.getForeignSource());
        }
        
        if (!nodeCriteria.getForeignId().isEmpty()) {
            alert.addLabel("foreign_id", nodeCriteria.getForeignId());
        }
        
        if (!nodeCriteria.getLocation().isEmpty()) {
            alert.addLabel("location", nodeCriteria.getLocation());
        }

        // Try to get enriched node info from cache
        Optional<EnrichedNode> enrichedNode = nodeCacheService.getNodeByCriteria(nodeCriteria);
        
        if (enrichedNode.isPresent()) {
            EnrichedNode node = enrichedNode.get();
            
            // Add node categories as label if configured
            if (properties.getAlert().getLabelMappings().isIncludeNodeCategories() &&
                !node.getCategories().isEmpty()) {
                String categories = String.join(",", node.getCategories());
                alert.addLabel(properties.getAlert().getLabelMappings().getCategoriesLabel(), categories);
            }

            // Add node metadata as labels if configured
            if (properties.getAlert().getLabelMappings().isIncludeNodeMetadata()) {
                String prefix = properties.getAlert().getLabelMappings().getNodeMetadataPrefix();
                for (Map.Entry<String, String> entry : node.getFlatMetadata().entrySet()) {
                    String labelKey = prefix + sanitizeMetadataKey(entry.getKey());
                    alert.addLabel(labelKey, entry.getValue());
                }
            }

            // Add sys info labels
            if (!node.getSysObjectId().isEmpty()) {
                alert.addLabel("sys_object_id", node.getSysObjectId());
            }
        }
    }

    /**
     * Add annotations to the alert.
     */
    private void addAnnotations(AlertmanagerAlert alert, OpennmsModelProtos.Alarm alarm) {
        BridgeProperties.AlertConfig.AnnotationMappings annConfig = 
                properties.getAlert().getAnnotationMappings();

        // Add summary from log message
        if (!alarm.getLogMessage().isEmpty()) {
            alert.addAnnotation("summary", alarm.getLogMessage());
        }

        // Add description
        if (annConfig.isIncludeDescription() && !alarm.getDescription().isEmpty()) {
            alert.addAnnotation("description", alarm.getDescription());
        }

        // Add operator instructions
        if (annConfig.isIncludeOperatorInstructions() && !alarm.getOperatorInstructions().isEmpty()) {
            alert.addAnnotation("runbook", alarm.getOperatorInstructions());
        }

        // Add alarm count
        alert.addAnnotation("alarm_count", String.valueOf(alarm.getCount()));

        // Add UEI
        alert.addAnnotation("opennms_uei", alarm.getUei());

        // Add acknowledgement info if present
        if (!alarm.getAckUser().isEmpty()) {
            alert.addAnnotation("acknowledged_by", alarm.getAckUser());
            if (alarm.getAckTime() > 0) {
                alert.addAnnotation("acknowledged_at", 
                        java.time.Instant.ofEpochMilli(alarm.getAckTime()).toString());
            }
        }

        // Add full node details as JSON if configured
        if (annConfig.isIncludeNodeDetails() && alarm.hasNodeCriteria()) {
            Optional<EnrichedNode> enrichedNode = 
                    nodeCacheService.getNodeByCriteria(alarm.getNodeCriteria());
            
            if (enrichedNode.isPresent()) {
                try {
                    String nodeJson = objectMapper.writeValueAsString(enrichedNode.get());
                    alert.addAnnotation(annConfig.getNodeDetailsKey(), nodeJson);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize node to JSON for alarm {}: {}", 
                            alarm.getId(), e.getMessage());
                }
            }
        }

        // Add related alarms summary if present
        if (alarm.getRelatedAlarmCount() > 0) {
            StringBuilder relatedSummary = new StringBuilder();
            for (OpennmsModelProtos.Alarm related : alarm.getRelatedAlarmList()) {
                relatedSummary.append(related.getReductionKey()).append("; ");
            }
            alert.addAnnotation("related_alarms", relatedSummary.toString());
        }
    }

    /**
     * Build alert name from UEI.
     */
    private String buildAlertName(String uei) {
        if (uei == null || uei.isEmpty()) {
            return "opennms_unknown";
        }

        String name = uei;
        if (name.startsWith("uei.opennms.org/")) {
            name = name.substring("uei.opennms.org/".length());
        } else if (name.startsWith("uei.")) {
            name = name.substring(4);
        }

        name = name.replaceAll("[^a-zA-Z0-9_]", "_");
        
        if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
            name = "_" + name;
        }

        return "opennms_" + name;
    }

    /**
     * Map OpenNMS severity to Prometheus severity string.
     */
    private String mapSeverity(OpennmsModelProtos.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case MAJOR -> "major";
            case MINOR -> "minor";
            case WARNING -> "warning";
            case NORMAL -> "info";
            case CLEARED -> "resolved";
            case INDETERMINATE -> "unknown";
            default -> "unknown";
        };
    }

    /**
     * Sanitize metadata key for use as Prometheus label.
     */
    private String sanitizeMetadataKey(String key) {
        return key.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * Check if an alarm should be forwarded based on configuration.
     */
    public boolean shouldForwardAlarm(OpennmsModelProtos.Alarm alarm) {
        if (!properties.getAlert().getIncludeSeverities().isEmpty()) {
            String severity = mapSeverity(alarm.getSeverity());
            if (!properties.getAlert().getIncludeSeverities().contains(severity)) {
                return false;
            }
        }

        if (properties.getAlert().getExcludeUeis().contains(alarm.getUei())) {
            return false;
        }

        return true;
    }
}
