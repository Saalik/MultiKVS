package com.bmartin.test;


import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.bmartin.kvs.*;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TestActor extends AbstractBehavior<TransactionManagerActor.BeginTransactionReplyEvent> {
    private int lastTransactionId = 0;

    public TestActor(ActorContext<TransactionManagerActor.BeginTransactionReplyEvent> context) {
        super(context);
    }

    public static Behavior<TransactionManagerActor.BeginTransactionReplyEvent> create() {
        return Behaviors.setup(TestActor::new);
    }

    @Override
    public Receive<TransactionManagerActor.BeginTransactionReplyEvent> createReceive() {
        return newReceiveBuilder()
                .onMessage(TransactionManagerActor.BeginTransactionReplyEvent.class, this::onReply)
                .build();
    }

    private Behavior<TransactionManagerActor.BeginTransactionReplyEvent> onReply(TransactionManagerActor.BeginTransactionReplyEvent e) {
        ++nbReceivedReplies;
        assertEquals(lastTransactionId++, e.transaction.id);
        return this;
    }

    public static int nbReceivedReplies = 0;
}


public class KeyValueStoreTest {
    // TODO: use BehaviorTestKit (https://doc.akka.io/docs/akka/current/typed/testing.html)
    ActorSystem<SpawnProtocol.Command> kvsSystem = null;

    @BeforeEach
    public void setUp() {
        final int numberOfShards = ConfigFactory.load().getInt("akka.cluster.sharding.number-of-shards");
        assertTrue(numberOfShards > 0);
        kvsSystem = ActorSystem.create(KeyValueStoreActorSystem.create(), KeyValueStoreActorSystem.NAME);
    }

    @AfterEach
    public void tearDown() {
        try {
            kvsSystem.terminate();
            kvsSystem.getWhenTerminated().toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        } finally {
            kvsSystem = null;
        }
    }

    @Test
    public void transactionEquality() {
        Transaction t1 = new Transaction(1, null);
        Transaction t2 = new Transaction(1, null);
        Transaction t3 = new Transaction(2, null);

        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
    }

    @Test
    public void testTransactionCoordinator() {
        assertEquals(0, TestActor.nbReceivedReplies);
        // TODO

//        ActorRef<TransactionManagerActor.BeginTransactionReplyEvent> testActor = ActorSystem.create(TestActor.create(), "TestActor");
//        ActorRef<TransactionManagerActor.Command> transactionCoordinatorActor = ActorSystem.create(TransactionManagerActor.create(null), "TransactionCoordinator");
//        transactionCoordinatorActor.tell(new TransactionManagerActor.BeginTransactionEvent(testActor));
//        transactionCoordinatorActor.tell(new TransactionManagerActor.BeginTransactionEvent(testActor));
//
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException ignored) {
//        } finally {
//            assertEquals(2, TestActor.nbReceivedReplies);
//        }
    }

    @Test
    public void kvsClientBeginTransactionTest() {
        assertNotNull(kvsSystem);
        KeyValueStoreClient client = new KeyValueStoreClient(kvsSystem);
        try {
            assertEquals(0, client.beginTransaction().id);
            assertEquals(1, client.beginTransaction().id);
            assertEquals(2, client.beginTransaction().id);
        } catch (ExecutionException | InterruptedException e) {
            fail(e);
        } finally {
            kvsSystem.terminate();
        }
    }

    @Test
    public void kvsClientPutGetCommitTest() {
        assertNotNull(kvsSystem);
        KeyValueStoreClient client = new KeyValueStoreClient(kvsSystem);
        try {
            Transaction t = client.beginTransaction();
            assertEquals(0, t.id);

            client.put(t, 0, "hello");
            assertEquals("hello", client.get(t, 0));

            client.put(t, 1, "world");
            assertEquals("world", client.get(t, 1));

            assertTrue(client.commitTransaction(t));

            // has been committed, should still be able to get
            assertEquals("hello", client.get(t, 0));
            assertEquals("world", client.get(t, 1));


            client.put(t, 0, "hi");
            assertEquals("hi", client.get(t, 0));

            assertNull(client.get(t, 2));

        } catch (ExecutionException | InterruptedException e) {
            fail(e);
        } finally {
            kvsSystem.terminate();
        }
    }

    @Test
    public void kvsClientPutGetAbortTest() {
        assertNotNull(kvsSystem);
        KeyValueStoreClient client = new KeyValueStoreClient(kvsSystem);
        try {
            Transaction t = client.beginTransaction();
            assertEquals(0, t.id);

            client.put(t, 0, "hello");
            assertEquals("hello", client.get(t, 0));

            client.put(t, 1, "world");
            assertEquals("world", client.get(t, 1));

            assertTrue(client.abortTransaction(t));     // ABORT HERE

            assertNull(client.get(t, 0));
            assertNull(client.get(t, 1));

            assertFalse(client.commitTransaction(t));    // nothing to commit

            assertNull(client.get(t, 0));
            assertNull(client.get(t, 1));


        } catch (ExecutionException | InterruptedException e) {
            fail(e);
        } finally {
            kvsSystem.terminate();
        }
    }

}

