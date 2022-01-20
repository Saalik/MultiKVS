package com.bmartin.kvs.monitoring;

import java.lang.management.PlatformManagedObject;

public interface SystemStatusMXBean extends PlatformManagedObject {
    Long getUptime();
    Integer getClusterMembers();
    Integer getNodeAgentCount();
    String getGraphVertices();
    String getGraphEdges();
}
