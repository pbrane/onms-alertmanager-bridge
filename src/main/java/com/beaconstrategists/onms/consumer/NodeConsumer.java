package com.beaconstrategists.onms.consumer;

import com.beaconstrategists.onms.model.OpennmsModelProtos;
import com.beaconstrategists.onms.service.NodeCacheService;
import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for OpenNMS nodes topic.
 * Caches node information for enriching alarms with node metadata.
 */
@Slf4j
@Component
public class NodeConsumer {

    private final NodeCacheService nodeCacheService;
    private final Counter nodesReceivedCounter;
    private final Counter nodesParsedCounter;
    private final Counter nodesParseErrorCounter;
    private final Counter nodesTombstoneCounter;

    public NodeConsumer(NodeCacheService nodeCacheService,
                       MeterRegistry meterRegistry) {
        this.nodeCacheService = nodeCacheService;
        
        this.nodesReceivedCounter = Counter.builder("opennms.bridge.nodes.received")
                .description("Number of node messages received from Kafka")
                .register(meterRegistry);
        
        this.nodesParsedCounter = Counter.builder("opennms.bridge.nodes.parsed")
                .description("Number of nodes successfully parsed")
                .register(meterRegistry);
        
        this.nodesParseErrorCounter = Counter.builder("opennms.bridge.nodes.parse.errors")
                .description("Number of node parsing errors")
                .register(meterRegistry);
        
        this.nodesTombstoneCounter = Counter.builder("opennms.bridge.nodes.tombstones")
                .description("Number of node tombstones (deletions) received")
                .register(meterRegistry);
    }

    /**
     * Consume node messages from Kafka.
     * Messages are keyed by node criteria (foreignSource:foreignId or nodeId)
     * and contain GPB-encoded node data.
     */
    @KafkaListener(
            topics = "${opennms.bridge.topics.nodes:nodes}",
            groupId = "${spring.kafka.consumer.group-id:opennms-alertmanager-bridge}",
            containerFactory = "nodeKafkaListenerContainerFactory"
    )
    public void consumeNode(ConsumerRecord<String, byte[]> record) {
        nodesReceivedCounter.increment();
        
        String nodeKey = record.key();
        byte[] value = record.value();

        log.debug("Received node message: key={}, partition={}, offset={}", 
                nodeKey, record.partition(), record.offset());

        // Handle tombstone (deleted node)
        if (value == null || value.length == 0) {
            nodesTombstoneCounter.increment();
            log.info("Received tombstone for node: {}", nodeKey);
            nodeCacheService.removeNode(nodeKey);
            return;
        }

        // Parse protobuf message
        try {
            OpennmsModelProtos.Node node = OpennmsModelProtos.Node.parseFrom(value);
            nodesParsedCounter.increment();
            
            log.debug("Parsed node: id={}, label={}, foreignSource={}, foreignId={}, categories={}, metadata={}", 
                    node.getId(), 
                    node.getLabel(),
                    node.getForeignSource(),
                    node.getForeignId(),
                    node.getCategoryList(),
                    node.getMetaDataCount());

            // Cache the node
            nodeCacheService.cacheNode(node);
            
        } catch (InvalidProtocolBufferException e) {
            nodesParseErrorCounter.increment();
            log.error("Failed to parse node protobuf message for key {}: {}", 
                    nodeKey, e.getMessage());
        }
    }
}
