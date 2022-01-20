package com.bmartin.kvs.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.jmx.shaded.io.prometheus.client.Collector;
import io.prometheus.jmx.shaded.io.prometheus.client.Info;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class NodeGraphCollector extends Collector {
    MBeanServer beanServer;
    ObjectInstance objectInstance;
    ObjectMapper objectMapper = new ObjectMapper();

    public NodeGraphCollector() throws MalformedObjectNameException, InstanceNotFoundException {
        this.beanServer = ManagementFactory.getPlatformMBeanServer();
        this.objectInstance = beanServer.getObjectInstance(ObjectName.getInstance(MonitoringActor.KVS_MXBEAN_NAME));
    }

    void addGraphMetrics(List<MetricFamilySamples> sampleFamilies) {
//        GaugeMetricFamily vertices = new GaugeMetricFamily("kvs_graph_vertices_info", "", List.of("id", "title", "subtitle", "mainStat", "secondaryStat", "arc__success", "arc__errors"));

        try {
            String verticesJson = (String) beanServer.getAttribute(objectInstance.getObjectName(), "GraphVertices");
            List<MonitoringActor.FormattedGraphVertices> vertices = objectMapper.readValue(verticesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MonitoringActor.FormattedGraphVertices.class));

            String edgesJson = (String) beanServer.getAttribute(objectInstance.getObjectName(), "GraphEdges");
            List<MonitoringActor.FormattedGraphEdges> edges = objectMapper.readValue(edgesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MonitoringActor.FormattedGraphEdges.class));


            Info verticesInfo = Info.build().name("kvs_graph_vertices").help("TODO")
                    .labelNames("id", "title", "subTitle", "mainStat", "secondaryStat", "arc__success", "arc__errors")
                    .create();

            Info edgesInfo = Info.build().name("kvs_graph_edges").help("TODO")
                    .labelNames("id", "source", "target", "mainStat", "secondaryStat")
                    .create();

            for (MonitoringActor.FormattedGraphVertices vertex : vertices) {
                // {id=\"0\", title=\"root\", subTitle=\"client\", mainStat=\"0.263\", secondaryStat=\"0.448\", arc__success=\"1\", arc__errors=\"0\"} 1.0
                // TODO: check this. It seems weird that the label values have to be set twice !
                Info.Child child = verticesInfo.labels(vertex.id, vertex.title, vertex.subTitle, vertex.mainStat, vertex.secondaryStat, vertex.arc__success, vertex.arc__errors);
                verticesInfo.setChild(child, vertex.id, vertex.title, vertex.subTitle, vertex.mainStat, vertex.secondaryStat, vertex.arc__success, vertex.arc__errors);
            }

            for (MonitoringActor.FormattedGraphEdges edge : edges) {
                Info.Child child = edgesInfo.labels(edge.id, edge.source, edge.target, edge.mainStat, edge.secondaryStat);
                edgesInfo.setChild(child, edge.id, edge.source, edge.target, edge.mainStat, edge.secondaryStat);
            }

            sampleFamilies.addAll(verticesInfo.collect());
            sampleFamilies.addAll(edgesInfo.collect());

        } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        this.addGraphMetrics(mfs);
        return mfs;
    }
}
