package com.bmartin.test;


import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.receptionist.Receptionist;
import com.bmartin.kvs.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ShardingTest {

    @BeforeEach
    public void setUp() {

        final int numberOfShards = ConfigFactory.load().getInt("akka.cluster.sharding.number-of-shards");
        assertTrue(numberOfShards > 0);
    }

    @AfterEach
    public void tearDown() {
//        testKit.shutdownTestKit();
    }

//    @Test
//    public void toto() {
//        final ActorTestKit testKit = ActorTestKit.create(ConfigFactory.load());
//
//        ClusterSharding sharding = ClusterSharding.get(testKit.system());
//
//        ActorRef<ShardingEnvelope<JournalActor.Command>> journalShard = sharding.init(
//                Entity.of(JournalActor.SHARD_KEY,
//                        ctx -> JournalActor.create(PersistenceId.of(JournalActor.SHARD_KEY.name(), ctx.getEntityId()))));
//
//        ActorRef<TransactionManagerActor.Command> transactionManager = testKit.spawn(TransactionManagerActor.create(journalShard));
//        TestProbe<TransactionManagerActor.BeginTransactionReplyEvent> transactionManagerProbe = testKit.createTestProbe();
//
//        transactionManager.tell(new TransactionManagerActor.BeginTransactionEvent(transactionManagerProbe.ref()));
//
//        TransactionManagerActor.BeginTransactionReplyEvent response = transactionManagerProbe.receiveMessage();
//        assertEquals(0, response.transaction.id);
//
//
//        TestProbe<TransactionCoordinatorActor.Command> transactionCoordinatorProbe = testKit.createTestProbe();
//
//        for (int i = 0; i < 10; ++i) {
//            response.transaction.actor.tell(new TransactionCoordinatorActor.PutEvent<>(response.transaction, i, "hello-" + i));
//        }
//
//        System.out.println(sharding.shardState());
//
//        testKit.shutdownTestKit();
//    }

    private void checkTransactionManager(ActorSystem<?> system) throws ExecutionException, InterruptedException {
        CompletionStage<Receptionist.Listing> result =
                AskPattern.ask(system.receptionist(),
                        replyTo -> Receptionist.find(TransactionManagerActor.TRANSACTION_MANAGER_ACTOR_SERVICE_KEY, replyTo),
                        Duration.ofSeconds(10),
                        system.scheduler());

        Set<ActorRef<TransactionManagerActor.Command>> listing = result.toCompletableFuture().get().getServiceInstances(TransactionManagerActor.TRANSACTION_MANAGER_ACTOR_SERVICE_KEY);


        assertEquals(1, listing.size());
    }

    @Test
    public void multiSystemTest() throws ExecutionException, InterruptedException {

        Config config1 = ConfigFactory.parseString("akka.cluster.jmx.multi-mbeans-in-same-jvm=on \n" +
                "akka.remote.artery.canonical.port=25520 \n" +
                "akka.cluster.seed-nodes=[\"akka://ShardingTest@127.0.0.1:25520\"] \n");
        final ActorTestKit testKit1 = ActorTestKit.create("ShardingTest", config1.withFallback(ConfigFactory.load()));

        Config config2 = ConfigFactory.parseString("akka.cluster.jmx.multi-mbeans-in-same-jvm=on \n" +
                "akka.remote.artery.canonical.port=25521 \n" +
                "akka.cluster.seed-nodes=[\"akka://ShardingTest@127.0.0.1:25520\"] \n");
        final ActorTestKit testKit2 = ActorTestKit.create("ShardingTest", config2.withFallback(ConfigFactory.load()));

        KeyValueStoreActorSystem.Wait wait = new KeyValueStoreActorSystem.Wait();

        ActorRef<SpawnProtocol.Command> kvs1 = testKit1.spawn(KeyValueStoreActorSystem.create("KVS1", wait), "KVS1");
        synchronized (wait) {
            wait.wait();
        }

        ActorRef<SpawnProtocol.Command> kvs2 = testKit2.spawn(KeyValueStoreActorSystem.create("KVS2", wait), "KVS2");
        synchronized (wait) {
            wait.wait();
        }

        checkTransactionManager(testKit1.system());
        checkTransactionManager(testKit2.system());

        KeyValueStoreClient kvs1Client = new KeyValueStoreClient(testKit1.system());
        Transaction transaction1 = kvs1Client.beginTransaction();
        assertEquals(0, transaction1.id);
        kvs1Client.put(transaction1, 1, "hello-1");

        KeyValueStoreClient kvs2Client = new KeyValueStoreClient(testKit2.system());
        Transaction transaction2 = kvs2Client.beginTransaction();
        assertEquals(1, transaction2.id);
        kvs2Client.put(transaction2, 2, "hello-2");


        checkTransactionManager(testKit1.system());
        checkTransactionManager(testKit2.system());

        System.out.println("shutdown !!");
        testKit1.shutdownTestKit();
        testKit2.shutdownTestKit();
    }


//    @Test
//    public void kvsShardingTest() {
//        assertNotNull(kvsSystem);
//        KeyValueStoreClient client = new KeyValueStoreClient(kvsSystem);
//        int numberOfShards = ConfigFactory.load().getInt("akka.cluster.sharding.number-of-shards");
//        assertTrue(numberOfShards > 0);
//        try {
//            Transaction t = client.beginTransaction();
//            assertEquals(0, t.id);
//
//            for (int i = 0; i < numberOfShards * 10; ++i) {
//                client.put(t, i, "hello-" + i);
//                assertNull(backend.read(KeyValueStoreActorSystem.getEntityId(i, numberOfShards), 0));
//            }
//
//            assertTrue(client.commitTransaction(t));    // commit
//
//            Thread.sleep(1000);
//
//            for (int i = 0; i < numberOfShards * 10; ++i) {
//
//                // check internals: private maps field in backend
//                for (Field field : backend.getClass().getDeclaredFields()) {
//                    System.out.println("Field Name: " + field.getName());
//                    if (field.getName().equals("maps")) {
//                        field.setAccessible(true);
//                        Map<String, HashMap<Integer, String>> maps = (Map<String, HashMap<Integer, String>>) field.get(backend);
//                        assertEquals(numberOfShards, maps.size());
//
//                        for (HashMap<Integer, String> map : maps.values()) {
//                            assertEquals(10, map.size());
//                        }
//                    }
//                }
//                assertEquals("hello-" + i, backend.read(KeyValueStoreActorSystem.getEntityId(i, numberOfShards), i));
//            }
//
//
//        } catch (ExecutionException | InterruptedException | IllegalAccessException e) {
//            fail(e);
//        } finally {
//            kvsSystem.terminate();
//        }
//    }
//
//    private static Behavior<KeyValueStoreActorSystem.Command> rootBehavior() {
//        return Behaviors.setup(context -> {
//            return Behaviors.empty();
//        });
//    }
//
//    @Test
//    public void multiJvmTest() throws ExecutionException, InterruptedException {
//
//        Map<String, Object> overrides = new HashMap<>();
//        overrides.put("akka.remote.artery.canonical.port", 0);
//
//        Config config = ConfigFactory.parseMap(overrides).withFallback(ConfigFactory.load());
//
//        ActorSystem<KeyValueStoreActorSystem.Command> externalSystem = ActorSystem.create(rootBehavior(), "KVSClient", config);
//        assertNotNull(externalSystem);
//
//        KeyValueStoreClient client = new KeyValueStoreClient(externalSystem);
//        Transaction t = client.beginTransaction();
//
//        externalSystem.terminate();
//    }

}

