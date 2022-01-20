package com.bmartin.kvs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.MemberStatus;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.Join;
import akka.cluster.typed.SingletonActor;
import akka.persistence.typed.PersistenceId;
import com.bmartin.kvs.journal.JournalActor;
import com.bmartin.kvs.monitoring.MonitoringActor;
import com.bmartin.kvs.monitoring.NodeMonitoringActor;

public abstract class KeyValueStoreActorSystem<K, V> {
    public static final String NAME = "KVSSystem";
    public static final short MAX_CLUSTER_CONNECT_RETRY = 5;
    public static final ServiceKey<SpawnProtocol.Command> SERVICE_KEY
            = ServiceKey.create(SpawnProtocol.Command.class, "KeyValueStoreActorSystem");


    public static final class Wait {
    }

    public static <K, V> Behavior<SpawnProtocol.Command> create() {
        return KeyValueStoreActorSystem.create(null, null);
    }

    public static <K, V> Behavior<SpawnProtocol.Command> create(String name, Wait wait) {
        return Behaviors.setup(context -> {
            if (null != name && !name.isEmpty()) {
                context.setLoggerName(name);
            }
            context.getLog().debug("KeyValueStoreActorSystem::create()");

            // register to receptionist
            context.getSystem().receptionist().tell(Receptionist.register(SERVICE_KEY, context.getSelf()));

            // Cluster
            Cluster cluster = Cluster.get(context.getSystem());
            assert (null != cluster);

            // init cluster listener
            context.spawn(ClusterListener.create(cluster), "ClusterListener");

            // wait for cluster init. TODO: can this be done in a better way ?
            short i = 0;
            do {
                try {
                    ++i;
                    context.getLog().debug("Cluster state is " + cluster.selfMember().status() +
                            ". Retry " + i + "/" + MAX_CLUSTER_CONNECT_RETRY);

                    if (MAX_CLUSTER_CONNECT_RETRY == i) {
                        throw new InstantiationException("Could not connect to the cluster.");
                    }

                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new InstantiationException("Thread sleep was interrupted.");
                }
            } while ((cluster.selfMember().status() != MemberStatus.up()));

            context.getLog().debug("Joining cluster");
            cluster.manager().tell(Join.create(cluster.selfMember().address()));

            context.getLog().debug("Setting up sharding");
            ClusterSharding sharding = ClusterSharding.get(context.getSystem());

            context.getLog().debug("Setting up journal shard");
            ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard =
                    sharding.init(Entity.of(
                            JournalActor.SHARD_KEY,
                            ctx -> JournalActor.create(
                                    PersistenceId.of(JournalActor.SHARD_KEY.name(), ctx.getEntityId()))));

            ClusterSingleton singleton = ClusterSingleton.get(context.getSystem());

            context.getLog().debug("Setting up MonitoringActor cluster singleton");
            // monitoring cluster singleton
            // TODO: maybe only on seed nodes ?
            ActorRef<MonitoringActor.Command> monitoringActor = singleton.init(SingletonActor.of(MonitoringActor.create(context.getSelf()), "MonitoringActor"));

            context.getLog().debug("Setting up NodeMonitoringActor");
            context.spawn(NodeMonitoringActor.create(monitoringActor, context.getSelf()), "NodeMonitoringActor");

            context.getLog().debug("Setting up TransactionManager cluster singleton");
            // Start if needed and provide a proxy to a named singleton
            singleton.init(SingletonActor.of(TransactionManagerActor.create(journalShard, monitoringActor), "TransactionCoordinator"));
            // TODO: handle cases when transactionManagerActor fails

            if (null != wait) {
                synchronized (wait) {
                    wait.notify();
                }
            }

            return SpawnProtocol.create();
        });
    }
}
