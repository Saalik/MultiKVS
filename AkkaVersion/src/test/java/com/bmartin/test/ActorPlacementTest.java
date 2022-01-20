package com.bmartin.test;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.*;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.bmartin.kvs.JsonSerializable;
import com.bmartin.kvs.KeyValueStoreActorSystem;
import com.bmartin.kvs.SpawnProtocol;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class MyTestBehavior extends AbstractBehavior<MyTestBehavior.Command> {
    public interface Command extends JsonSerializable {
    }

    public static final class TestMessage implements Command {
        @JsonCreator
        public TestMessage(ActorRef<Boolean> replyTo) {
            System.out.println("TestMessage::TestMessage() : replyTo=" + replyTo);
            this.replyTo = replyTo;
        }

        ActorRef<Boolean> replyTo;
    }

    public MyTestBehavior(ActorContext<Command> context) {
        super(context);
        System.out.println("MyTestBehavior::MyTestBehavior(): " + this.getContext().getSelf());
        hasCtorBeenCalled = true;
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            context.getLog().debug("MyTestBehavior::create()");
            return new MyTestBehavior(context);
        });
    }

    @Override
    public Receive<Command> createReceive() {
        getContext().getLog().debug("MyTestBehavior::createReceive()");
        return newReceiveBuilder()
                .onMessage(TestMessage.class, this::onTestMessage)
                .build();
    }

    private Behavior<Command> onTestMessage(final TestMessage e) {
        getContext().getLog().debug("MyTestBehavior::onTestMessage(): e=" + e);
        hasEventBeenReceived = true;

        // send response
        e.replyTo.tell(true);

        return Behaviors.same();
    }

    public static boolean hasCtorBeenCalled = false;
    public static boolean hasEventBeenReceived = false;
}

public class ActorPlacementTest {

    @BeforeEach
    public void setUp() {
        final int numberOfShards = ConfigFactory.load().getInt("akka.cluster.sharding.number-of-shards");
        assertTrue(numberOfShards > 0);

        MyTestBehavior.hasCtorBeenCalled = false;
        MyTestBehavior.hasEventBeenReceived = false;
    }

    @AfterEach
    public void tearDown() {
    }


    static class SpawnEvent<_T> {
        public String id;
        public SpawnProtocol.SerializableRunnable<_T> runnable;

        @JsonCreator
        public SpawnEvent(@JsonProperty("id") String id,
                          @JsonProperty("runnable") SpawnProtocol.SerializableRunnable<_T> runnable) {
            this.id = id;
            this.runnable = runnable;
        }
    }


    @Test
    public void serialiseBehaviorTest() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        SpawnProtocol.SerializableRunnable<MyTestBehavior.Command> action = (context) -> {
            context.getLog().debug("MyTestBehavior::create()");
//            assertTrue(context instanceof akka.actor.ActorContext);
            return new MyTestBehavior(context);
        };

        String json = mapper.writeValueAsString(new SpawnEvent<>("1", action));
        assertFalse(json.isEmpty());
        System.out.println(json);
        System.out.println(json.length());

        SpawnEvent<MyTestBehavior.Command> deserializedMessage = mapper.readValue(json, SpawnEvent.class);

        ActorTestKit testKit = ActorTestKit.create(EventSourcedBehaviorTestKit.config().withFallback(ConfigFactory.load()));

        System.out.println("Spawn now !");
        testKit.spawn(Behaviors.<MyTestBehavior.Command>setup(context -> deserializedMessage.runnable.apply(context)));

        testKit.shutdownTestKit();
    }

    @Test
    public void multiInstanceTest() throws InterruptedException, ExecutionException {
        Config config1 = ConfigFactory.parseString("akka.remote.artery.canonical.port=25520 \n" +
                "akka.cluster.seed-nodes=[\"akka://multiInstanceTest@127.0.0.1:25520\"] \n");
        final ActorTestKit testKit1 = ActorTestKit.create("multiInstanceTest", config1.withFallback(ConfigFactory.load()));

        Config config2 = ConfigFactory.parseString("akka.remote.artery.canonical.port=25521 \n" +
                "akka.cluster.seed-nodes=[\"akka://multiInstanceTest@127.0.0.1:25520\"] \n");
        final ActorTestKit testKit2 = ActorTestKit.create("multiInstanceTest", config2.withFallback(ConfigFactory.load()));

        KeyValueStoreActorSystem.Wait wait = new KeyValueStoreActorSystem.Wait();

        ActorRef<SpawnProtocol.Command> kvs1 = testKit1.spawn(KeyValueStoreActorSystem.create("KVS1", wait), "KVS1");

        synchronized (wait) {
            wait.wait();
        }
        ActorRef<SpawnProtocol.Command> kvs2 = testKit2.spawn(KeyValueStoreActorSystem.create("KVS2", wait), "KVS2");

        synchronized (wait) {
            wait.wait();
        }


        // prepare remote behavior parameters
        SpawnProtocol.SerializableRunnable<MyTestBehavior.Command> action = (context) -> {
//            assertTrue(context instanceof akka.actor.ActorContext);
            context.getLog().debug("MyTestBehavior::create()");
            return new MyTestBehavior(context);
        };

        CompletionStage<ActorRef<MyTestBehavior.Command>> result =
                AskPattern.ask(
                        kvs2,       // target remote actor system
                        replyTo ->
                                new SpawnProtocol.Spawn<>(action, "MyTestBehavior", Props.empty(), replyTo),
                        Duration.ofSeconds(3),
                        testKit1.system().scheduler()); // source actor system scheduler

        // wait for actor to be spawned
        ActorRef<MyTestBehavior.Command> spawnedActor = result.toCompletableFuture().get();
        assertNotNull(spawnedActor);

        assertEquals("MyTestBehavior", spawnedActor.path().name());
        assertEquals("KVS2", spawnedActor.path().parent().name());

        // send message to spawned actor
        CompletionStage<Boolean> response =
                AskPattern.ask(
                        spawnedActor,
                        replyTo -> new MyTestBehavior.TestMessage(replyTo),
                        Duration.ofSeconds(3),
                        testKit1.system().scheduler());
        assertTrue(response.toCompletableFuture().get());

        assertTrue(MyTestBehavior.hasCtorBeenCalled);
        assertTrue(MyTestBehavior.hasEventBeenReceived);

        testKit1.shutdownTestKit();
        testKit2.shutdownTestKit();
    }
}
