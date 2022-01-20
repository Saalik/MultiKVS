# This project is a joint work with Benoit Martin (Benoit.Martin@lip6.fr)
For reference I reuploaded the project here

You can still find the original repository here:
https://gitlab.lip6.fr/martin/kvs

# TODO/DONE
(in no specific order)

- [ ] assert pre/post conditions
- [ ] Checkpointing
- [ ] Refactor names to correspond to specification document
- [x] Check multi-JVM sharding
- [x] HTTP entrypoint
- [ ] Query by status (ie: committed)
- [x] Explore explicit Actor placement
- [ ] Multi-DC
- [x] Query by key
- [x] Query by timestamp
- [x] Console client application
- [x] Docker


# HTTP server

curl -H "Content-Type: application/json" -X GET http://localhost:8080/begin

curl -H "Content-Type: application/json" -X PUT -d '{"id":0, "key":42, "value":"hello"}' http://localhost:8080/put

# Custom SpawnProtocol

Actors can now be spawned on a remote node without having to serialize the Actor's behavior.

``` java
// prepare remote behavior parameters
SpawnProtocol.SerializableRunnable<MyTestBehavior.Command> action = (context) -> {
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
```

# Technical debt
- JournalTest.java: Journal State converts int type to String due to https://github.com/akka/akka/issues/28566

# Docker and Docker-Compose

A little tweak/hack is currently needed for the console application to work from outside a docker context.
Add the following to /etc/hosts: `127.0.0.1 host.docker.internal kvs-server-seed`


## build
```docker-compose build```

## run
start all services
```docker-compose up -d```

start a shell in a container that is on the same network as the kvs services 
```docker run -it --rm --network kvs_default kvs-server```


