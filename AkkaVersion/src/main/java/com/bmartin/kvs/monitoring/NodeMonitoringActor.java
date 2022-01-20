package com.bmartin.kvs.monitoring;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.bmartin.kvs.SpawnProtocol;

/**
 * NodeMonitoringActor is run on all nodes in the actor system and enables the construction of a node graph.
 */
public class NodeMonitoringActor extends AbstractBehavior<MonitoringActor.Command> {
    private final ActorRef<MonitoringActor.Command> monitoringActor;
    private final ActorRef<SpawnProtocol.Command> rootActor;

    public static Behavior<MonitoringActor.Command> create(ActorRef<MonitoringActor.Command> monitoringActor,
                                                           ActorRef<SpawnProtocol.Command> rootActor) {
        return Behaviors.setup((context) -> new NodeMonitoringActor(context, monitoringActor, rootActor));
    }

    public NodeMonitoringActor(ActorContext<MonitoringActor.Command> context,
                               ActorRef<MonitoringActor.Command> monitoringActor,
                               ActorRef<SpawnProtocol.Command> rootActor) {
        super(context);
        getContext().getLog().debug("NodeMonitoringActor::NodeMonitoringActor(): monitoringActor=" + monitoringActor);
        assert (null != monitoringActor);
        this.monitoringActor = monitoringActor;
        this.rootActor = rootActor;

        this.monitoringActor.tell(new MonitoringActor.HelloEvent(rootActor, getContext().getSelf()));
    }

    @Override
    public Receive<MonitoringActor.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(MonitoringActor.HeartbeatEvent.class, this::onHeartbeat)
                .onMessage(MonitoringActor.HeartbeatReplyEvent.class, this::onHeartbeatReplyEvent)
                .build();
    }

    private Behavior<MonitoringActor.Command> onHeartbeat(MonitoringActor.HeartbeatEvent e) {
        getContext().getLog().debug("NodeMonitoringActor::onHeartbeat()");

        if (null != e.others) {
            // send HeartbeatEvent to others
            for (MonitoringActor.Vertex other : e.others) {
                if (getContext().getSelf().path() != other.actor.path()) {
                    // don't send to self or to MonitoringActor
                    other.actor.tell(new MonitoringActor.HeartbeatEvent(getContext().getSelf()));
                }
            }
        }

        e.from.tell(new MonitoringActor.HeartbeatReplyEvent(this.rootActor, getContext().getSelf(), e));

        return Behaviors.same();
    }

    private Behavior<MonitoringActor.Command> onHeartbeatReplyEvent(MonitoringActor.HeartbeatReplyEvent e) {
        getContext().getLog().debug("NodeMonitoringActor::onHeartbeatReplyEvent()");

        long latency = (System.currentTimeMillis() - e.startTimeStamp) / 2; // RTT/2
        // reply from HeartbeatEvent sent to others
        monitoringActor.tell(new MonitoringActor.HeartbeatReplyFromOtherEvent(
                this.rootActor, getContext().getSelf(),
                e.fromRootActor, e.from, latency));

        return Behaviors.same();
    }
}
