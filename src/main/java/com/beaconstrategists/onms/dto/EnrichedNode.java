package com.beaconstrategists.onms.dto;

import com.beaconstrategists.onms.model.OpennmsModelProtos;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enriched node DTO that includes full node details and custom metadata.
 * This is used for including complete node information in Alertmanager annotations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EnrichedNode {

    private long id;
    private String foreignSource;
    private String foreignId;
    private String location;
    private String label;
    private long createTime;
    private String sysContact;
    private String sysDescription;
    private String sysObjectId;
    
    @Builder.Default
    private List<String> categories = new ArrayList<>();
    
    @Builder.Default
    private List<IpInterfaceInfo> ipInterfaces = new ArrayList<>();
    
    @Builder.Default
    private List<SnmpInterfaceInfo> snmpInterfaces = new ArrayList<>();
    
    /**
     * Custom metadata from OpenNMS (context -> key -> value)
     */
    @Builder.Default
    private Map<String, Map<String, String>> metadata = new HashMap<>();
    
    /**
     * Flattened metadata for easy access (context:key -> value)
     */
    @Builder.Default
    private Map<String, String> flatMetadata = new HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class IpInterfaceInfo {
        private long id;
        private String ipAddress;
        private int ifIndex;
        private String primaryType;
        private List<String> services;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class SnmpInterfaceInfo {
        private long id;
        private int ifIndex;
        private String ifDescr;
        private int ifType;
        private String ifName;
        private long ifSpeed;
        private String ifPhysAddress;
        private int ifAdminStatus;
        private int ifOperStatus;
        private String ifAlias;
    }

    /**
     * Create an EnrichedNode from protobuf Node message.
     */
    public static EnrichedNode fromProto(OpennmsModelProtos.Node node) {
        if (node == null) {
            return null;
        }

        EnrichedNodeBuilder builder = EnrichedNode.builder()
                .id(node.getId())
                .foreignSource(node.getForeignSource())
                .foreignId(node.getForeignId())
                .location(node.getLocation())
                .label(node.getLabel())
                .createTime(node.getCreateTime())
                .sysContact(node.getSysContact())
                .sysDescription(node.getSysDescription())
                .sysObjectId(node.getSysObjectId())
                .categories(new ArrayList<>(node.getCategoryList()));

        // Convert IP interfaces
        List<IpInterfaceInfo> ipInterfaces = new ArrayList<>();
        for (OpennmsModelProtos.IpInterface iface : node.getIpInterfaceList()) {
            ipInterfaces.add(IpInterfaceInfo.builder()
                    .id(iface.getId())
                    .ipAddress(iface.getIpAddress())
                    .ifIndex(iface.getIfIndex())
                    .primaryType(iface.getPrimaryType().name())
                    .services(new ArrayList<>(iface.getServiceList()))
                    .build());
        }
        builder.ipInterfaces(ipInterfaces);

        // Convert SNMP interfaces
        List<SnmpInterfaceInfo> snmpInterfaces = new ArrayList<>();
        for (OpennmsModelProtos.SnmpInterface iface : node.getSnmpInterfaceList()) {
            snmpInterfaces.add(SnmpInterfaceInfo.builder()
                    .id(iface.getId())
                    .ifIndex(iface.getIfIndex())
                    .ifDescr(iface.getIfDescr())
                    .ifType(iface.getIfType())
                    .ifName(iface.getIfName())
                    .ifSpeed(iface.getIfSpeed())
                    .ifPhysAddress(iface.getIfPhysAddress())
                    .ifAdminStatus(iface.getIfAdminStatus())
                    .ifOperStatus(iface.getIfOperStatus())
                    .ifAlias(iface.getIfAlias())
                    .build());
        }
        builder.snmpInterfaces(snmpInterfaces);

        // Convert metadata if present
        Map<String, Map<String, String>> metadata = new HashMap<>();
        Map<String, String> flatMetadata = new HashMap<>();
        for (OpennmsModelProtos.MetaData md : node.getMetaDataList()) {
            String context = md.getContext();
            String key = md.getKey();
            String value = md.getValue();
            
            metadata.computeIfAbsent(context, k -> new HashMap<>()).put(key, value);
            flatMetadata.put(context + ":" + key, value);
        }
        builder.metadata(metadata);
        builder.flatMetadata(flatMetadata);

        return builder.build();
    }

    /**
     * Get metadata value by context and key.
     */
    public String getMetadataValue(String context, String key) {
        Map<String, String> contextMap = metadata.get(context);
        return contextMap != null ? contextMap.get(key) : null;
    }

    /**
     * Get all metadata for a specific context.
     */
    public Map<String, String> getMetadataForContext(String context) {
        return metadata.getOrDefault(context, new HashMap<>());
    }
}
