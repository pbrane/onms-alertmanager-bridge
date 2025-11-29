package com.beaconstrategists.onms.consumer;

import com.beaconstrategists.onms.model.OpennmsModelProtos;
import com.beaconstrategists.onms.service.ActiveAlarmCacheService;
import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for OpenNMS alarms topic.
 * Processes incoming alarm messages and forwards them to Alertmanager.
 */
@Slf4j
@Component
public class AlarmConsumer {

    private final ActiveAlarmCacheService activeAlarmCacheService;
    private final Counter alarmsReceivedCounter;
    private final Counter alarmsParsedCounter;
    private final Counter alarmsParseErrorCounter;
    private final Counter alarmsTombstoneCounter;

    public AlarmConsumer(ActiveAlarmCacheService activeAlarmCacheService,
                        MeterRegistry meterRegistry) {
        this.activeAlarmCacheService = activeAlarmCacheService;
        
        this.alarmsReceivedCounter = Counter.builder("opennms.bridge.alarms.received")
                .description("Number of alarm messages received from Kafka")
                .register(meterRegistry);
        
        this.alarmsParsedCounter = Counter.builder("opennms.bridge.alarms.parsed")
                .description("Number of alarms successfully parsed")
                .register(meterRegistry);
        
        this.alarmsParseErrorCounter = Counter.builder("opennms.bridge.alarms.parse.errors")
                .description("Number of alarm parsing errors")
                .register(meterRegistry);
        
        this.alarmsTombstoneCounter = Counter.builder("opennms.bridge.alarms.tombstones")
                .description("Number of alarm tombstones (deletions) received")
                .register(meterRegistry);
    }

    /**
     * Consume alarm messages from Kafka.
     * Messages are keyed by reduction key and contain GPB-encoded alarm data.
     * Null values (tombstones) indicate alarm deletion.
     */
    @KafkaListener(
            topics = "${opennms.bridge.topics.alarms:alarms}",
            groupId = "${spring.kafka.consumer.group-id:opennms-alertmanager-bridge}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAlarm(ConsumerRecord<String, byte[]> record) {
        alarmsReceivedCounter.increment();
        
        String reductionKey = record.key();
        byte[] value = record.value();

        log.debug("Received alarm message: key={}, partition={}, offset={}", 
                reductionKey, record.partition(), record.offset());

        // Handle tombstone (deleted alarm)
        if (value == null || value.length == 0) {
            alarmsTombstoneCounter.increment();
            log.info("Received tombstone for alarm: {}", reductionKey);
            activeAlarmCacheService.handleDeletedAlarm(reductionKey);
            return;
        }

        // Parse protobuf message
        try {
            OpennmsModelProtos.Alarm alarm = OpennmsModelProtos.Alarm.parseFrom(value);
            alarmsParsedCounter.increment();
            
            log.debug("Parsed alarm: id={}, uei={}, severity={}, reductionKey={}", 
                    alarm.getId(), 
                    alarm.getUei(), 
                    alarm.getSeverity(), 
                    alarm.getReductionKey());

            // Process the alarm
            activeAlarmCacheService.updateAlarm(alarm);
            
        } catch (InvalidProtocolBufferException e) {
            alarmsParseErrorCounter.increment();
            log.error("Failed to parse alarm protobuf message for key {}: {}", 
                    reductionKey, e.getMessage());
        }
    }
}
