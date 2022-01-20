package com.bmartin.kvs.monitoring;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.typed.Cluster;
import com.bmartin.kvs.CborSerializable;
import com.bmartin.kvs.SpawnProtocol;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.*;

/**
 * NodeMonitoringActor is run on all nodes in the actor system and enables the construction of a node graph.
 */
public class MonitoringActor extends AbstractBehavior<MonitoringActor.Command> implements SystemStatusMXBean {
    static final String KVS_MXBEAN_NAME = "com.bmartin.kvs:name=Metrics";
    public static final ServiceKey<MonitoringActor.Command> MONITORING_ACTOR_SERVICE_KEY
            = ServiceKey.create(MonitoringActor.Command.class, "MonitoringActor");
    public static final int HEARTBEAT_FREQUENCY = 10;

    static class Vertex {
        public Vertex(ActorRef<SpawnProtocol.Command> rootActor, ActorRef<Command> actor) {
            assert (null != rootActor);
            assert (null != actor);
            this.rootActor = rootActor;
            this.actor = actor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Vertex that = (Vertex) o;
            return rootActor.equals(that.rootActor) && actor.equals(that.actor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rootActor, actor);
        }

        final ActorRef<SpawnProtocol.Command> rootActor;
        final ActorRef<Command> actor;
    }

    static class Edge extends DefaultWeightedEdge {
        ActorRef<SpawnProtocol.Command> fromRootActor;
        ActorRef<SpawnProtocol.Command> toRootActor;
        long latency = Long.MAX_VALUE;

        Edge(ActorRef<SpawnProtocol.Command> fromRootActor, ActorRef<SpawnProtocol.Command> toRootActor) {
            this.fromRootActor = fromRootActor;
            this.toRootActor = toRootActor;
        }

        @Override
        public String toString() {
            return Long.toString(latency);
        }
    }

    public interface Command extends CborSerializable {
    }

    /**
     * Event sent from node agent
     */
    static class HelloEvent implements Command {
        @JsonCreator
        HelloEvent(ActorRef<SpawnProtocol.Command> rootActor, ActorRef<Command> actor) {
            this.rootActor = rootActor;
            this.from = actor;
        }

        ActorRef<SpawnProtocol.Command> rootActor;
        ActorRef<Command> from;
    }

    static class HeartbeatEvent implements Command {
        HeartbeatEvent(ActorRef<Command> from) {
            this(from, null);
        }

        @JsonCreator
        HeartbeatEvent(ActorRef<Command> from, Set<Vertex> others) {
            this.startTimeStamp = System.currentTimeMillis();
            this.from = from;
            this.others = others;
        }

        long startTimeStamp;
        ActorRef<Command> from;
        Set<Vertex> others;
    }

    static class HeartbeatReplyFromOtherEvent implements Command {
        @JsonCreator
        HeartbeatReplyFromOtherEvent(ActorRef<SpawnProtocol.Command> fromRootActor, ActorRef<Command> from,
                                     ActorRef<SpawnProtocol.Command> toRootActor, ActorRef<Command> to, long latency) {
            this.fromRootActor = fromRootActor;
            this.from = from;
            this.toRootActor = toRootActor;
            this.to = to;
            this.latency = latency;
        }

        ActorRef<SpawnProtocol.Command> fromRootActor;
        ActorRef<Command> from;
        ActorRef<SpawnProtocol.Command> toRootActor;
        ActorRef<Command> to;
        long latency;
    }

    static class HeartbeatReplyEvent implements Command {
        HeartbeatReplyEvent(ActorRef<SpawnProtocol.Command> fromRootActor,
                            ActorRef<Command> from, HeartbeatEvent heartbeatEvent) {
            this.fromRootActor = fromRootActor;
            this.from = from;
            this.startTimeStamp = heartbeatEvent.startTimeStamp;
        }

        @JsonCreator
        HeartbeatReplyEvent(ActorRef<SpawnProtocol.Command> fromRootActor, ActorRef<Command> from, long startTimeStamp) {
            this.fromRootActor = fromRootActor;
            this.from = from;
            this.startTimeStamp = startTimeStamp;
        }

        ActorRef<SpawnProtocol.Command> fromRootActor;
        ActorRef<Command> from;
        long startTimeStamp;
    }

    public static class QueryEvent implements Command {
        public QueryEvent(ActorRef<?> from, ActorRef<ActorRef<SpawnProtocol.Command>> replyTo) {
            this.from = from;
            this.replyTo = replyTo;
        }

        ActorRef<?> from;
        ActorRef<ActorRef<SpawnProtocol.Command>> replyTo;
    }

    public static Behavior<Command> create(ActorRef<SpawnProtocol.Command> rootActor) {
        return Behaviors.setup(context -> {
            // register to receptionist
            context.getSystem().receptionist()
                    .tell(Receptionist.register(MONITORING_ACTOR_SERVICE_KEY, context.getSelf()));
            return new MonitoringActor(context, rootActor);
        });
    }

    public MonitoringActor(ActorContext<Command> context, ActorRef<SpawnProtocol.Command> rootActor) {
        super(context);
        getContext().getLog().debug("MonitoringActor::MonitoringActor()");

        this.rootActor = rootActor;
        this.cluster = Cluster.get(getContext().getSystem());
        this.actorGraph = new DefaultDirectedWeightedGraph<>(Edge.class);
        actorGraph.addVertex(new Vertex(rootActor, getContext().getSelf()));

        // this is needed for jackson to serialize without having to add getters/setters
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        // periodically send heartbeat event to known actors
        getContext().getSystem().scheduler().scheduleWithFixedDelay(
                Duration.ofSeconds(10), // initial delay
                Duration.ofSeconds(HEARTBEAT_FREQUENCY), // delay
                () -> {
                    Set<Vertex> vertices = actorGraph.vertexSet();
                    for (Vertex vertex : vertices) {
                        if (getContext().getSelf() != vertex.actor) {
                            // don't send HeartbeatEvent to self
                            vertex.actor.tell(new HeartbeatEvent(getContext().getSelf(), vertices));
                        }
                    }
                },
                getContext().getExecutionContext());

        try {
            // Register the object in the MBeanServer
            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            platformMBeanServer.registerMBean(this, new ObjectName(KVS_MXBEAN_NAME));
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
            e.printStackTrace();
            // TODO
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(HelloEvent.class, this::onHello)
                .onMessage(MonitoringActor.HeartbeatEvent.class, this::onHeartbeat)
                .onMessage(HeartbeatReplyEvent.class, this::onHeartbeatReply)
                .onMessage(HeartbeatReplyFromOtherEvent.class, this::onHeartbeatReplyFromOtherEvent)
                .onMessage(QueryEvent.class, this::onQueryEvent)
                .build();
    }

    private Behavior<Command> onHello(HelloEvent e) {
        getContext().getLog().debug("MonitoringActor::onHello()");
        this.actorGraph.addVertex(new Vertex(e.rootActor, e.from));

        Vertex from = new Vertex(this.rootActor, getContext().getSelf());
        Vertex to = new Vertex(e.rootActor, e.from);
        this.actorGraph.addEdge(from, to, new Edge(this.rootActor, e.rootActor));
        this.actorGraph.addEdge(to, from, new Edge(e.rootActor, this.rootActor));
        return Behaviors.same();
    }

    private Behavior<Command> onHeartbeat(HeartbeatEvent e) {
        getContext().getLog().debug("MonitoringActor::onHeartbeat()");
        e.from.tell(new HeartbeatReplyEvent(this.rootActor, getContext().getSelf(), e));
        return Behaviors.same();
    }

    private Behavior<Command> onHeartbeatReply(HeartbeatReplyEvent e) {
        getContext().getLog().debug("MonitoringActor::onHeartbeatReply()");
        assert (getContext().getSelf() != e.from);
        assert (getContext().getSelf().path() != e.from.path());

        Vertex from = new Vertex(this.rootActor, getContext().getSelf());
        Vertex to = new Vertex(e.fromRootActor, e.from);
        Edge edge = actorGraph.getEdge(from, to);
        assert (null != edge);

        edge.latency = (System.currentTimeMillis() - e.startTimeStamp) / 2; // RTT/2

        return Behaviors.same();
    }

    private Behavior<Command> onHeartbeatReplyFromOtherEvent(HeartbeatReplyFromOtherEvent e) {
        getContext().getLog().debug("MonitoringActor::onHeartbeatReplyFromOtherEvent()");
        assert (e.from != e.to);
        assert (e.from.path() != e.to.path());

        Vertex from = new Vertex(e.fromRootActor, e.from);
        Vertex to = new Vertex(e.toRootActor, e.to);
        Edge edge = actorGraph.getEdge(from, to);
        if (null == edge) {
            actorGraph.addEdge(from, to, new Edge(e.fromRootActor, e.toRootActor));
            edge = actorGraph.getEdge(from, to);
        }

        edge.latency = e.latency;

        return Behaviors.same();
    }

    private Behavior<Command> onQueryEvent(QueryEvent e) {
        getContext().getLog().debug("MonitoringActor::onQueryEvent()");
        assert (null != e.from);

        // find the client's NodeMonitoringActor
        Vertex clientVertex = actorGraph.vertexSet().stream()
                .filter(vertexInfo -> vertexInfo.actor.path().root() == e.from.path().root())
                .findFirst().orElse(null);
        assert (null != clientVertex);

        // find the NodeMonitoringActor closest to the client's
        Edge edge = actorGraph.outgoingEdgesOf(clientVertex).stream()
                .min(Comparator.comparing(nodeInfo -> nodeInfo.latency))
                .get();

        assert (actorGraph.getEdgeSource(edge).actor.path() == clientVertex.actor.path());

        e.replyTo.tell(edge.toRootActor);

        return Behaviors.same();
    }

    private void printNodeGraph() {
        for (Edge edge : actorGraph.edgeSet()) {
            ActorRef<?> src = actorGraph.getEdgeSource(edge).actor;
            ActorRef<?> dst = actorGraph.getEdgeTarget(edge).actor;

            System.out.println(src.path().name() + " (" + src.path().uid() + ") -- " + edge.latency + " --> " +
                    dst.path().name() + " (" + dst.path().uid() + ")");
        }
    }

    @Override
    public ObjectName getObjectName() {
        try {
            return ObjectName.getInstance(MonitoringActor.KVS_MXBEAN_NAME);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Long getUptime() {
        return getContext().getSystem().uptime();
    }

    @Override
    public Integer getClusterMembers() {
        return this.cluster.state().members().size();
    }

    @Override
    public Integer getNodeAgentCount() {
        return actorGraph.vertexSet().size();
    }

    static class FormattedGraphVertices {
        final String id;
        final String title;
        final String subTitle;
        final String mainStat;
        final String secondaryStat;
        final String arc__success;
        final String arc__errors;

        FormattedGraphVertices(ActorRef<Command> actorRef) {
            assert (null != actorRef);
            this.id = String.valueOf(actorRef.path().uid());
            this.title = actorRef.path().name();
            this.subTitle = String.valueOf(actorRef.path().uid());
            this.mainStat = "";
            this.secondaryStat = "";
            this.arc__success = "1.0";
            this.arc__errors = "0.0";
        }

        @JsonCreator
        public FormattedGraphVertices(@JsonProperty("id") String id,
                                      @JsonProperty("title") String title,
                                      @JsonProperty("subTitle") String subTitle,
                                      @JsonProperty("mainStat") String mainStat,
                                      @JsonProperty("secondaryStat") String secondaryStat,
                                      @JsonProperty("arc__success") String arc__success,
                                      @JsonProperty("arc__errors") String arc__errors) {
            this.id = id;
            this.title = title;
            this.subTitle = subTitle;
            this.mainStat = mainStat;
            this.secondaryStat = secondaryStat;
            this.arc__success = arc__success;
            this.arc__errors = arc__errors;
        }
    }

    static class FormattedGraphEdges {
        final String id;
        final String source;
        final String target;
        final String mainStat;
        final String secondaryStat;

        FormattedGraphEdges(Graph<Vertex, Edge> actorGraph, Edge nodeInfo) {
            assert (null != nodeInfo);
            int src = actorGraph.getEdgeSource(nodeInfo).actor.path().uid();
            int target = actorGraph.getEdgeTarget(nodeInfo).actor.path().uid();

            this.id = src + ":" + target;
            this.source = String.valueOf(src);
            this.target = String.valueOf(target);
            this.mainStat = String.valueOf(nodeInfo.latency);
            this.secondaryStat = "";
        }

        @JsonCreator
        public FormattedGraphEdges(@JsonProperty("id") String id,
                                   @JsonProperty("source") String source,
                                   @JsonProperty("target") String target,
                                   @JsonProperty("mainStat") String mainStat,
                                   @JsonProperty("secondaryStat") String secondaryStat) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.mainStat = mainStat;
            this.secondaryStat = secondaryStat;
        }
    }

    @Override
    public String getGraphVertices() {
        try {
            Set<Vertex> vertices = actorGraph.vertexSet();
            List<FormattedGraphVertices> res = new ArrayList<>(vertices.size());
            for (Vertex vertex : vertices) {
                res.add(new FormattedGraphVertices(vertex.actor));
            }
            return objectMapper.writeValueAsString(res);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String getGraphEdges() {
        try {
            Set<Edge> edges = actorGraph.edgeSet();
            List<FormattedGraphEdges> res = new ArrayList<>(edges.size());
            for (Edge info : edges) {
                res.add(new FormattedGraphEdges(actorGraph, info));
            }

            return objectMapper.writeValueAsString(res);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    private final ActorRef<SpawnProtocol.Command> rootActor;
    private final Cluster cluster;
    final Graph<Vertex, Edge> actorGraph;
    ObjectMapper objectMapper = new ObjectMapper();
}
