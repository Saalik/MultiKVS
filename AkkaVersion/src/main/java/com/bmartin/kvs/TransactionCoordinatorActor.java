package com.bmartin.kvs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.ShardingEnvelope;
import com.bmartin.kvs.journal.JournalActor;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * The role of the coordinator is to:
 * - start the transaction at each shard
 * - send each client operation to the correct shard
 * - coordinate the two-phase commit of the transaction among all the shards
 * <p>
 * Note: Should be close to the client.
 */
public class TransactionCoordinatorActor<K, V> extends AbstractBehavior<TransactionCoordinatorActor.Command> {

    public interface Command extends CborSerializable {
    }

    public static final class PutEvent<K, V> implements Command {
        public PutEvent(Transaction transaction, K key, V value) {
            this.transaction = transaction;
            this.key = key;
            this.value = value;
        }

        public final Transaction transaction;
        public final K key;
        public final V value;
    }

    public static final class GetEvent<K, V> implements Command {
        GetEvent(ActorRef<TransactionCoordinatorActor.GetReplyEvent<V>> replyTo, Transaction transaction, K key) {
            this.replyTo = replyTo;
            this.transaction = transaction;
            this.key = key;
        }

        public final ActorRef<TransactionCoordinatorActor.GetReplyEvent<V>> replyTo;
        public final Transaction transaction;
        public final K key;
    }

    public static final class GetReplyEvent<V> implements Command {
        @JsonCreator
        GetReplyEvent(V value) {
            this.value = value;
        }

        public final V value;
    }

    public static final class CommitEvent implements Command {
        CommitEvent(ActorRef<TransactionCoordinatorActor.CommitReplyEvent> replyTo, Transaction transaction) {
            this.replyTo = replyTo;
            this.transaction = transaction;
        }

        public final ActorRef<TransactionCoordinatorActor.CommitReplyEvent> replyTo;
        public final Transaction transaction;
    }

    public static final class CommitReplyEvent implements Command {
        @JsonCreator
        CommitReplyEvent(boolean isCommitted) {
            this.isCommitted = isCommitted;
        }


        public final boolean isCommitted;
    }

    public static final class AbortEvent implements Command {
        AbortEvent(ActorRef<TransactionCoordinatorActor.AbortEventReply> replyTo, Transaction transaction) {
            this.replyTo = replyTo;
            this.transaction = transaction;
        }

        public final ActorRef<TransactionCoordinatorActor.AbortEventReply> replyTo;
        public final Transaction transaction;
    }

    public static final class AbortEventReply implements Command {
        @JsonCreator
        AbortEventReply(boolean isAborted) {
            this.isAborted = isAborted;
        }

        public final boolean isAborted;
    }


    public static <K, V> Behavior<Command> create(int transactionId,
                                                  ActorRef<TransactionManagerActor.BeginTransactionReplyEvent> replyTo,
                                                  ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard) {
        return Behaviors.setup(context -> {
            context.getLog().debug("TransactionCoordinatorActor::create()");
            return new TransactionCoordinatorActor<K, V>(context, transactionId, replyTo, journalShard);
        });
    }

    public TransactionCoordinatorActor(ActorContext<Command> context,
                                       int transactionId,
                                       ActorRef<TransactionManagerActor.BeginTransactionReplyEvent> replyTo,
                                       ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard) {
        super(context);
        assert (transactionId >= 0);
        this.journalShard = journalShard;
        this.currentTransactions = new HashMap<>();
        getContext().getLog().debug("TransactionCoordinatorActor::TransactionCoordinatorActor(): transactionId=" + transactionId);

        // reply to client
        replyTo.tell(new TransactionManagerActor.BeginTransactionReplyEvent(new Transaction(transactionId, getContext().getSelf())));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PutEvent.class, this::onPut)
                .onMessage(GetEvent.class, this::onGet)
                .onMessage(CommitEvent.class, this::onCommit)
                .onMessage(AbortEvent.class, this::onAbort)
                .build();
    }

    private Behavior<Command> onPut(final PutEvent<K, V> e) {
        getContext().getLog().debug("TransactionCoordinatorActor::onPut(): e=" + e);
        assert (null != journalShard);

        List<K> l = currentTransactions.getOrDefault(e.transaction, new LinkedList<>());
        l.add(e.key);
        currentTransactions.put(e.transaction, l);

        // e.key is used as the entityId as AKKA sharding takes the hashCode of the entity identifier modulo the total number of shards.
        // ref: https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html#shard-allocation
        journalShard.tell(new ShardingEnvelope<>(getEntityId(e.key, getContext()),
                new JournalActor.Update<>(0, e.transaction, e.key, e.value)));

        // TODO persist actor state here ?

        return Behaviors.same();
    }

    /**
     * Read from inflightTransactions first. If nothing is found, read from Backend
     *
     * @param e
     * @return
     */
    private Behavior<Command> onGet(final Object e) {   // TODO: why doesn't this work with generics ?!
        getContext().getLog().debug("TransactionCoordinatorActor::onGet(): e=" + e);
        assert (null != journalShard);

        GetEvent<K, V> tmp = (GetEvent<K, V>) e; // TODO should be removed

        SortedSet<JournalActor.State.Record<K, V>> result = null;
        try {
            // ask journal
            CompletionStage<JournalActor.LookupReply<K, V>> ask =
                    AskPattern.ask(journalShard,
                            replyTo -> new ShardingEnvelope<>(getEntityId(tmp.key, getContext()),
                                    new JournalActor.Lookup<>(replyTo, tmp.transaction, tmp.key)),
//                            replyTo -> new JournalActor.Lookup<>(replyTo, tmp.key),
                            Duration.ofSeconds(3),
                            getContext().getSystem().scheduler());

            result = ask.toCompletableFuture().get().value; // blocking call
            System.out.println(">>>>> result=" + result);

        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            // interrupted, what should I do ?
            assert (false);   // TODO
        } catch (ExecutionException executionException) {
            executionException.printStackTrace();
            // timed out while asking the journal
            assert (false);   // TODO
        }

        // client can only ask for a key, thus result size is either 0 (not found) or 1
        assert (0 == result.size() || 1 == result.size());
        if (0 == result.size()) {
            tmp.replyTo.tell(new GetReplyEvent<>(null));
        } else {
            tmp.replyTo.tell(new GetReplyEvent<>(result.first().value));
        }

        return Behaviors.same();
    }

    private Behavior<Command> onCommit(final Object e) {
        getContext().getLog().debug("TransactionCoordinatorActor::onCommit(): e=" + e);
        assert (null != journalShard);

        CommitEvent tmp = (CommitEvent) e; // TODO should be removed
        List<K> l = currentTransactions.get(tmp.transaction);
        if (null != l && !l.isEmpty()) {
            // find entityid from key to update sharded journal
            Set<String> entities = new HashSet<>();

            for (K k : l) {
                entities.add(getEntityId(k, getContext()));
            }

            List<CompletionStage<JournalActor.CommitReply>> sentCommitEvents = new LinkedList<>();
            for (String entity : entities) {
                // async
//                journalShard.tell(new ShardingEnvelope<>(entity,
//                        new JournalActor.Commit(null, System.currentTimeMillis(), tmp.transaction)));

                // sync
                CompletionStage<JournalActor.CommitReply> ask =
                        AskPattern.ask(journalShard,
                                replyTo -> new ShardingEnvelope<>(entity,
                                        new JournalActor.Commit(replyTo, System.currentTimeMillis(), tmp.transaction)),
                                Duration.ofSeconds(3),
                                getContext().getSystem().scheduler());

                sentCommitEvents.add(ask);
            }

            // wait for results
            try {
                boolean finalResult = true;
                for (CompletionStage<JournalActor.CommitReply> ask : sentCommitEvents) {
                    finalResult = ask.toCompletableFuture().get().value; // blocking call
                    if (!finalResult) {
                        // if false, than there has been an error
                        // TODO: how to handle case when there is an error ?
                        tmp.replyTo.tell(new CommitReplyEvent(false));
                        break;
                    }
                }

                if (finalResult) {
                    // no error
                    currentTransactions.remove(tmp.transaction);
                    tmp.replyTo.tell(new CommitReplyEvent(true));
                    return Behaviors.stopped();
                }
            } catch (InterruptedException | ExecutionException ex) {
                // TODO: how to handle case when there is an error ?
                getContext().getLog().error(ex.getMessage());
                // timed out while asking the journal
                tmp.replyTo.tell(new CommitReplyEvent(false));
            }
        }

        return Behaviors.same();
    }

    private Behavior<Command> onAbort(final Object e) {
        getContext().getLog().debug("TransactionCoordinatorActor::onAbort(): e=" + e);
        assert (null != journalShard);

        AbortEvent tmp = (AbortEvent) e; // TODO should be removed

        List<K> l = currentTransactions.get(tmp.transaction);
        if (null != l && !l.isEmpty()) {
            // find entityid from key to update sharded journal
            Set<String> entities = new HashSet<>();

            for (K k : l) {
                entities.add(getEntityId(k, getContext()));
            }

            List<CompletionStage<JournalActor.AbortReply>> sentAbortEvents = new LinkedList<>();
            for (String entity : entities) {
                // async
//                journalShard.tell(new ShardingEnvelope<>(entity,
//                        new JournalActor.Abort(null, System.currentTimeMillis(), tmp.transaction)));

                // sync
                CompletionStage<JournalActor.AbortReply> ask =
                        AskPattern.ask(journalShard,
                                replyTo -> new ShardingEnvelope<>(entity,
                                        new JournalActor.Abort(replyTo, System.currentTimeMillis(), tmp.transaction)),
                                Duration.ofSeconds(3),
                                getContext().getSystem().scheduler());

                sentAbortEvents.add(ask);
            }

            // wait for results
            try {
                boolean finalResult = true;
                for (CompletionStage<JournalActor.AbortReply> ask : sentAbortEvents) {
                    finalResult = ask.toCompletableFuture().get().value; // blocking call
                    if (!finalResult) {
                        // if false, than there has been an error
                        // TODO: how to handle case when there is an error ?
                        tmp.replyTo.tell(new AbortEventReply(false));
                        break;
                    }
                }

                if (finalResult) {
                    // no error
                    currentTransactions.remove(tmp.transaction);
                    tmp.replyTo.tell(new AbortEventReply(true));
                    return Behaviors.stopped();
                }
            } catch (InterruptedException | ExecutionException ex) {
                // TODO: how to handle case when there is an error ?
                getContext().getLog().error(ex.getMessage());
                // timed out while asking the journal
                tmp.replyTo.tell(new AbortEventReply(false));
            }
        }

        return Behaviors.same();
    }


    /**
     * Get backend shard's entityId from key
     * TODO: an extra entity actor is created with an entityId of JournalActor--N. Why is there '--' instead of '-' !!!
     */
    private static <K> String getEntityId(K key, ActorContext<?> context) {
        final int nbShards = context.getSystem().settings().config().getInt("akka.cluster.sharding.number-of-shards");
        return getEntityId(key, nbShards);
    }

    private static <K> String getEntityId(K key, final int numberOfShards) {
        assert (numberOfShards > 0);
        return JournalActor.SHARD_KEY.name() + "-" + (key.hashCode() % numberOfShards);
    }

    final ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard;
    final Map<Transaction, List<K>> currentTransactions;
}
