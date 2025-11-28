package com.example.opennms.service;

import com.example.opennms.dto.EnrichedNode;
import com.example.opennms.model.OpennmsModelProtos;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for caching OpenNMS node information.
 * Nodes are cached by their criteria (foreignSource:foreignId or nodeId).
 */
@Slf4j
@Service
public class NodeCacheService {

    private final ConcurrentMap<String, EnrichedNode> nodeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> nodeIdToKeyMap = new ConcurrentHashMap<>();
    private final AtomicInteger cacheSize;

    public NodeCacheService(MeterRegistry meterRegistry) {
        this.cacheSize = meterRegistry.gauge("opennms.bridge.node.cache.size", 
                Tags.empty(), new AtomicInteger(0));
    }

    /**
     * Cache a node from protobuf message.
     */
    public void cacheNode(OpennmsModelProtos.Node node) {
        if (node == null) {
            return;
        }

        String key = buildNodeKey(node);
        EnrichedNode enrichedNode = EnrichedNode.fromProto(node);
        
        nodeCache.put(key, enrichedNode);
        nodeIdToKeyMap.put(node.getId(), key);
        cacheSize.set(nodeCache.size());

        log.debug("Cached node: {} (id={})", key, node.getId());
    }

    /**
     * Cache a pre-built enriched node.
     */
    public void cacheNode(String key, EnrichedNode node) {
        if (key == null || node == null) {
            return;
        }
        
        nodeCache.put(key, node);
        nodeIdToKeyMap.put(node.getId(), key);
        cacheSize.set(nodeCache.size());
    }

    /**
     * Get a node by its criteria key.
     */
    public Optional<EnrichedNode> getNode(String key) {
        return Optional.ofNullable(nodeCache.get(key));
    }

    /**
     * Get a node by its node ID.
     */
    public Optional<EnrichedNode> getNodeById(long nodeId) {
        String key = nodeIdToKeyMap.get(nodeId);
        if (key != null) {
            return Optional.ofNullable(nodeCache.get(key));
        }
        return Optional.empty();
    }

    /**
     * Get a node using NodeCriteria from an alarm.
     */
    public Optional<EnrichedNode> getNodeByCriteria(OpennmsModelProtos.NodeCriteria criteria) {
        if (criteria == null) {
            return Optional.empty();
        }

        // First try by foreignSource:foreignId
        if (!criteria.getForeignSource().isEmpty() && !criteria.getForeignId().isEmpty()) {
            String key = criteria.getForeignSource() + ":" + criteria.getForeignId();
            Optional<EnrichedNode> node = getNode(key);
            if (node.isPresent()) {
                return node;
            }
        }

        // Fall back to node ID
        if (criteria.getId() > 0) {
            return getNodeById(criteria.getId());
        }

        return Optional.empty();
    }

    /**
     * Remove a node from cache.
     */
    public void removeNode(String key) {
        EnrichedNode removed = nodeCache.remove(key);
        if (removed != null) {
            nodeIdToKeyMap.remove(removed.getId());
            cacheSize.set(nodeCache.size());
            log.debug("Removed node from cache: {}", key);
        }
    }

    /**
     * Remove a node by ID.
     */
    public void removeNodeById(long nodeId) {
        String key = nodeIdToKeyMap.remove(nodeId);
        if (key != null) {
            nodeCache.remove(key);
            cacheSize.set(nodeCache.size());
            log.debug("Removed node from cache by id: {}", nodeId);
        }
    }

    /**
     * Get all cached nodes.
     */
    public Collection<EnrichedNode> getAllNodes() {
        return nodeCache.values();
    }

    /**
     * Get cache size.
     */
    public int size() {
        return nodeCache.size();
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        nodeCache.clear();
        nodeIdToKeyMap.clear();
        cacheSize.set(0);
        log.info("Node cache cleared");
    }

    /**
     * Build cache key from protobuf node.
     */
    private String buildNodeKey(OpennmsModelProtos.Node node) {
        if (!node.getForeignSource().isEmpty() && !node.getForeignId().isEmpty()) {
            return node.getForeignSource() + ":" + node.getForeignId();
        }
        return String.valueOf(node.getId());
    }
}
