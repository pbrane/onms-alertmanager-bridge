package com.example.opennms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "opennms.bridge")
@Validated
public class BridgeProperties {

    /**
     * Alertmanager configuration
     */
    private Alertmanager alertmanager = new Alertmanager();

    /**
     * Kafka topics configuration
     */
    private Topics topics = new Topics();

    /**
     * Alert configuration
     */
    private AlertConfig alert = new AlertConfig();

    /**
     * OpenNMS configuration
     */
    private OpenNms opennms = new OpenNms();

    @Data
    public static class Alertmanager {
        /**
         * Alertmanager base URL (e.g., http://alertmanager:9093)
         */
        private String url = "http://localhost:9093";

        /**
         * API version path
         */
        private String apiPath = "/api/v2/alerts";

        /**
         * Connection timeout
         */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * Read timeout
         */
        private Duration readTimeout = Duration.ofSeconds(10);

        /**
         * Enable/disable forwarding to Alertmanager
         */
        private boolean enabled = true;

        /**
         * Retry configuration
         */
        private Retry retry = new Retry();

        @Data
        public static class Retry {
            private int maxAttempts = 3;
            private Duration backoff = Duration.ofSeconds(1);
        }
    }

    @Data
    public static class Topics {
        /**
         * Kafka topic for alarms
         */
        private String alarms = "alarms";

        /**
         * Kafka topic for nodes
         */
        private String nodes = "nodes";

        /**
         * Kafka topic for events (optional)
         */
        private String events = "events";
    }

    @Data
    public static class AlertConfig {
        /**
         * Interval for re-sending active alerts to Alertmanager
         */
        private Duration resendInterval = Duration.ofMinutes(1);

        /**
         * How long to keep resolved alerts before removing from cache
         */
        private Duration resolvedRetention = Duration.ofMinutes(5);

        /**
         * Static labels to add to all alerts
         */
        private Map<String, String> staticLabels = new HashMap<>();

        /**
         * Label mappings from OpenNMS alarm fields
         */
        private LabelMappings labelMappings = new LabelMappings();

        /**
         * Annotation mappings
         */
        private AnnotationMappings annotationMappings = new AnnotationMappings();

        /**
         * Severities to include (empty means all)
         */
        private Set<String> includeSeverities = new HashSet<>();

        /**
         * UEIs to exclude from forwarding
         */
        private Set<String> excludeUeis = new HashSet<>();

        @Data
        public static class LabelMappings {
            /**
             * Include node metadata as labels
             */
            private boolean includeNodeMetadata = true;

            /**
             * Prefix for node metadata labels
             */
            private String nodeMetadataPrefix = "opennms_meta_";

            /**
             * Include node categories as labels
             */
            private boolean includeNodeCategories = true;

            /**
             * Label name for categories (comma-separated)
             */
            private String categoriesLabel = "opennms_categories";
        }

        @Data
        public static class AnnotationMappings {
            /**
             * Include full node JSON in annotations
             */
            private boolean includeNodeDetails = true;

            /**
             * Annotation key for node details
             */
            private String nodeDetailsKey = "opennms_node";

            /**
             * Include alarm description
             */
            private boolean includeDescription = true;

            /**
             * Include operator instructions
             */
            private boolean includeOperatorInstructions = true;
        }
    }

    @Data
    public static class OpenNms {
        /**
         * OpenNMS base URL for generating links
         */
        private String baseUrl = "http://localhost:8980/opennms";
    }
}
