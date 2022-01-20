package com.bmartin.kvs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.receptionist.Receptionist;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


public class KeyValueStoreClient {
    public static final short DEFAULT_MAX_RETRY = 5;
    public static final long DEFAULT_RETRY_TIMEOUT = 500;   // in ms
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public static class TransactionCoordinatorNotFoundRuntimeException extends RuntimeException {
        public TransactionCoordinatorNotFoundRuntimeException(String s) {
            super(s);
        }

        public TransactionCoordinatorNotFoundRuntimeException(Throwable t) {
            super(t);
        }
    }


    public KeyValueStoreClient(ActorSystem<?> actorSystem) {
        System.out.println("KeyValueStoreClient::KeyValueStoreClient()");

        this.actorSystem = actorSystem;
        this.transactionManager = null;

        // TODO: is this really needed ?
//        ClusterSharding sharding = ClusterSharding.get(actorSystem);
//        sharding.init(Entity.of(BackendActor.SHARD_KEY, ctx -> BackendActor.create(null, ctx.getEntityId())));

        getTransactionManager();
    }

    private void getTransactionManager() throws TransactionCoordinatorNotFoundRuntimeException {
        assert (null != actorSystem);

        CompletionStage<Receptionist.Listing> result =
                AskPattern.ask(actorSystem.receptionist(),
                        replyTo -> Receptionist.find(TransactionManagerActor.TRANSACTION_MANAGER_ACTOR_SERVICE_KEY, replyTo),
                        DEFAULT_TIMEOUT,
                        actorSystem.scheduler());


        try {
            // blocking call
            Set<ActorRef<TransactionManagerActor.Command>> listing = result.toCompletableFuture().get().getServiceInstances(TransactionManagerActor.TRANSACTION_MANAGER_ACTOR_SERVICE_KEY);
            if (listing.isEmpty()) {
                if (++retry < DEFAULT_MAX_RETRY + 1) {
                    final long timeout = DEFAULT_RETRY_TIMEOUT * retry;
                    actorSystem.log().info("KeyValueStoreClient::getTransactionManager() retry " + retry + "/" + DEFAULT_MAX_RETRY + ", timeout=" + timeout);
                    // sleep and retry
                    Thread.sleep(timeout);
                    getTransactionManager();
                } else {
                    throw new TransactionCoordinatorNotFoundRuntimeException("Could not find TransactionManager after " + DEFAULT_MAX_RETRY + " retries.");
                }
            } else {
                this.transactionManager = listing.iterator().next();    // TODO: if more than 1 result, use closest
                actorSystem.log().info("KeyValueStoreClient::getTransactionManager(): found TransactionManager " + this.transactionManager +
                        " out of " + listing.size());
            }
        } catch (ExecutionException e) {
            throw new TransactionCoordinatorNotFoundRuntimeException(e.getCause());
        } catch (InterruptedException e) {
            throw new TransactionCoordinatorNotFoundRuntimeException(e);
        }
    }

    /**
     * Note: blocking call: will wait for response or throw an exception
     *
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public Transaction beginTransaction() throws ExecutionException, InterruptedException {
        assert (null != actorSystem);
        assert (null != transactionManager);

        CompletionStage<TransactionManagerActor.BeginTransactionReplyEvent> result =
                AskPattern.ask(transactionManager,
                        replyTo -> new TransactionManagerActor.BeginTransactionEvent(replyTo),
                        DEFAULT_TIMEOUT,
                        actorSystem.scheduler());

        return result.toCompletableFuture().get().transaction;    // blocking call
    }

    public boolean commitTransaction(Transaction transaction) throws ExecutionException, InterruptedException {
        assert (null != actorSystem);
        assert (transaction.id >= 0);
        assert (null != transaction.actor);

        CompletionStage<TransactionCoordinatorActor.CommitReplyEvent> result =
                AskPattern.ask(transaction.actor,
                        replyTo -> new TransactionCoordinatorActor.CommitEvent(replyTo, transaction),
                        DEFAULT_TIMEOUT,
                        actorSystem.scheduler());

        return result.toCompletableFuture().get().isCommitted;
    }

    public boolean abortTransaction(Transaction transaction) throws ExecutionException, InterruptedException {
        assert (null != actorSystem);
        assert (transaction.id >= 0);
        assert (null != transaction.actor);

        CompletionStage<TransactionCoordinatorActor.AbortEventReply> result =
                AskPattern.ask(transaction.actor,
                        replyTo -> new TransactionCoordinatorActor.AbortEvent(replyTo, transaction),
                        DEFAULT_TIMEOUT,
                        actorSystem.scheduler());

        return result.toCompletableFuture().get().isAborted;
    }

    public <K, V> void put(Transaction transaction, K key, V value) {
        assert (null != actorSystem);
        assert (null != transaction);
        assert (transaction.id >= 0);
        assert (null != transaction.actor);
        transaction.actor.tell(new TransactionCoordinatorActor.PutEvent<>(transaction, key, value));
    }

    public <K, V> V get(Transaction t, K key) throws ExecutionException, InterruptedException {
        assert (null != actorSystem);
        assert (t.id >= 0);
        assert (null != t.actor);

        CompletionStage<TransactionCoordinatorActor.GetReplyEvent<V>> result =
                AskPattern.ask(t.actor,
                        replyTo -> new TransactionCoordinatorActor.GetEvent<>(replyTo, t, key),
                        DEFAULT_TIMEOUT,
                        actorSystem.scheduler());

        return result.toCompletableFuture().get().value;
    }

    public String getConnectionInfo() {
        return actorSystem.address().hostPort();    // TODO: this is wrong, should be remote host and port not local
    }

    public void disconnect() {
        actorSystem.terminate();
    }

    private short retry = 0;
    private final ActorSystem<?> actorSystem;
    private ActorRef<TransactionManagerActor.Command> transactionManager;
}
