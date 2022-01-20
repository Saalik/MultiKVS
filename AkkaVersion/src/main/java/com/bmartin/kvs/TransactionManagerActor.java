package com.bmartin.kvs;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.sharding.typed.ShardingEnvelope;
import com.bmartin.kvs.journal.JournalActor;
import com.bmartin.kvs.monitoring.MonitoringActor;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * The TransactionManager receives transactions from the clients.
 * When a transaction is started, a TransactionCoordinator is started to manage it.
 */
public class TransactionManagerActor extends AbstractBehavior<TransactionManagerActor.Command> {
    public static final ServiceKey<Command> TRANSACTION_MANAGER_ACTOR_SERVICE_KEY
            = ServiceKey.create(Command.class, "TransactionManagerActor");

    public interface Command extends CborSerializable {
    }

    public static class BeginTransactionEvent implements Command {
        @JsonCreator
        public BeginTransactionEvent(ActorRef<BeginTransactionReplyEvent> replyTo) {
            this.replyTo = replyTo;
        }

        ActorRef<BeginTransactionReplyEvent> replyTo;
    }

    public static class BeginTransactionReplyEvent implements Command {
        @JsonCreator
        BeginTransactionReplyEvent(Transaction transaction) {
            assert (transaction.id >= 0);
            this.transaction = transaction;
        }

        public final Transaction transaction;
    }

    public static Behavior<Command> create(ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard,
                                           ActorRef<MonitoringActor.Command> monitoringActor) {
        return Behaviors.setup(context -> {
            // register to receptionist
            context.getSystem().receptionist()
                    .tell(Receptionist.register(TRANSACTION_MANAGER_ACTOR_SERVICE_KEY, context.getSelf()));
            return new TransactionManagerActor(context, journalShard, monitoringActor);
        });
    }

    public TransactionManagerActor(ActorContext<Command> context,
                                   ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard,
                                   ActorRef<MonitoringActor.Command> monitoringActor) {
        super(context);
        getContext().getSystem().log().debug("TransactionManagerActor::TransactionManagerActor()");
        this.journalShard = journalShard;
        this.monitoringActor = monitoringActor;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(BeginTransactionEvent.class, this::onBeginTransaction)
                .onSignal(Terminated.class, this::onTerminated) // must be handled or if a child actor fails, parent will also fail
                .onSignal(ChildFailed.class, this::onChildFailed) // must be handled or if a child actor fails, parent will also fail
                .build();
    }

    /**
     * Called when a watched ActorRef is Terminated
     *
     * @param terminated message
     * @return same behavior
     */
    private Behavior<Command> onTerminated(Terminated terminated) {
        getContext().getSystem().log().info("Job stopped: {}", terminated.getRef().path().name());
        return this;
    }

    /**
     * Called when a watched ActorRef has failed
     *
     * @param childFailed message
     * @return same behavior
     */
    private Behavior<Command> onChildFailed(ChildFailed childFailed) {
        getContext().getSystem().log().warn("Child has failed: {}", childFailed.getRef().path().name());
        // TODO: test this
        return this;
    }

    /**
     * spawn TransactorCoordinator and monitors it
     *
     * @param e new transaction event
     * @return same behavior
     */
    private Behavior<Command> onBeginTransaction(BeginTransactionEvent e) {
        getContext().getSystem().log().debug("TransactionManagerActor::onBeginTransaction() e=" + e);
        assert (null != journalShard);
        assert (null != monitoringActor);

        try {
            CompletionStage<ActorRef<SpawnProtocol.Command>> targetActorSystemRes =
                    AskPattern.ask(
                            monitoringActor,       // target remote actor system
                            replyTo ->
                                    new MonitoringActor.QueryEvent(getContext().getSelf(), replyTo),
                            Duration.ofSeconds(3),
                            getContext().getSystem().scheduler()); // source actor system scheduler
            ActorRef<SpawnProtocol.Command> targetActorSystem = targetActorSystemRes.toCompletableFuture().get();

            getContext().getLog().debug("Got targetActorSystem=: " + targetActorSystem);

            CompletionStage<ActorRef<TransactionCoordinatorActor.Command>> result =
                    AskPattern.ask(
                            targetActorSystem,       // target remote actor system
                            replyTo ->
                                    new SpawnProtocol.Spawn<>(
                                            (context) -> new TransactionCoordinatorActor<>(context, lastTransactionId, e.replyTo, journalShard),
                                            "Transaction-" + lastTransactionId,
                                            Props.empty(),
                                            replyTo),
                            Duration.ofSeconds(3),
                            getContext().getSystem().scheduler()); // source actor system scheduler

            // wait for actor to be spawned
            ActorRef<TransactionCoordinatorActor.Command> spawnedActor = result.toCompletableFuture().get();
            getContext().getLog().debug("Remotely spawn TransactionCoordinatorActor: " + spawnedActor);

        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        } catch (ExecutionException executionException) {
            executionException.printStackTrace();
        }


//        Behavior<TransactionCoordinatorActor.Command> b = TransactionCoordinatorActor.create(lastTransactionId, e.replyTo, journalShard);
//        ActorRef<TransactionCoordinatorActor.Command> ref = getContext().spawn(b, "Transaction-" + lastTransactionId);  // TODO placement (near client) ?

//        getContext().watch(ref);
//        Behaviors.supervise(b).onFailure(SupervisorStrategy.restart());
        // TODO: recover transaction on failure

        ++lastTransactionId;
        return this;
    }

    int lastTransactionId = 0;
    private final ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard;
    private final ActorRef<MonitoringActor.Command> monitoringActor;
}
