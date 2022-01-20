package com.bmartin.kvs;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.bmartin.kvs.monitoring.JMXExporter;
import com.bmartin.kvs.monitoring.MonitoringActor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class MainServer extends AllDirectives {
    private static final String DEFAULT_CLUSTER_IP = System.getenv("HOSTNAME") == null ? "127.0.0.1" : System.getenv("HOSTNAME");
    private static final String DEFAULT_CLUSTER_PORT = "25520";
    private static final String DEFAULT_SEED = "akka://KVSSystem@" + DEFAULT_CLUSTER_IP + ":25520";
    private static final String DEFAULT_HTTP_PORT = "8080";

    public static void main(String[] args) throws InterruptedException {

        Options options = new Options();
        Option clusterIp = new Option("ip", "clusterip", true, "cluster ip");
        Option clusterPort = new Option("p", "clusterport", true, "cluster port");
        Option seed = new Option("s", "seed-nodes", true, "seed nodes");
        Option httpListen = new Option("l", "http-listen", true, "http listen port");
//        input.setRequired(true);
        options.addOption(clusterIp);
        options.addOption(clusterPort);
        options.addOption(seed);
        options.addOption(httpListen);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("KeyValueStore", options);
            System.exit(1);
        }

        assert (null != cmd);
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("akka.remote.artery.canonical.hostname", cmd.getOptionValue("clusterip", DEFAULT_CLUSTER_IP));
        overrides.put("akka.remote.artery.canonical.port", cmd.getOptionValue("clusterport", DEFAULT_CLUSTER_PORT));
        overrides.put("akka.cluster.seed-nodes", Arrays.asList(cmd.getOptionValue("seed-nodes", DEFAULT_SEED).split(",")));
        overrides.put("akka.cluster.roles", Collections.singletonList("backend"));
        Config config = ConfigFactory.parseMap(overrides).withFallback(ConfigFactory.load());

        KeyValueStoreActorSystem.Wait wait = new KeyValueStoreActorSystem.Wait();

        ActorSystem<SpawnProtocol.Command> kvsSystem =
                ActorSystem.create(KeyValueStoreActorSystem.create(null, wait), KeyValueStoreActorSystem.NAME, config);

        synchronized (wait) {
            wait.wait();
        }

        MainServer app = null;
        try {
            app = new MainServer(kvsSystem);
            app.startHttp(Integer.parseInt(cmd.getOptionValue("http-listen", DEFAULT_HTTP_PORT)));
        } catch (Exception e) {
            kvsSystem.log().error(e.toString());
            if (null != app) {
                app.exit(-1);
            }
        }
    }


    MainServer(final ActorSystem<SpawnProtocol.Command> kvsSystem) {
        this.kvsSystem = kvsSystem;
        this.client = new KeyValueStoreClient(kvsSystem);
        this.transactions = new HashMap<>();
        this.jmxExporter = new JMXExporter(kvsSystem.log());
    }


    private Route createRoute() {
        return concat(
                path("begin", () -> concat(
                        get(() -> {
                            Transaction transaction = null;
                            try {
                                transaction = client.beginTransaction();
                                this.transactions.put(transaction.id, transaction);
                            } catch (Throwable t) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR, "ERROR: TODO");
                            }

                            return completeOK(new TransactionPOJO(transaction), Jackson.marshaller());
//                            return completeOK(transaction, Jackson.marshaller());
//                            return complete(StatusCodes.OK, String.valueOf(transaction.id));
                        })
                )),
                path("put", () -> concat(
                        put(() ->
                                entity(Jackson.unmarshaller(TransactionPOJO.class), transactionPojo -> {
                                    Transaction transaction = this.transactions.get(transactionPojo.id);
                                    client.put(transaction, transactionPojo.key, transactionPojo.value);
                                    return complete(StatusCodes.OK, "OK");
                                })
                        )
                )),
                path("metrics", () -> concat(
                        get(() -> extractRequest(r -> {
                            return complete(StatusCodes.OK, jmxExporter.getJmxMetrics(r));
                        }))
                ))
                /*TODO: only used to test grafana's csv datasource,
                pathPrefix("metrics_graph", () -> concat(
                        path("nodes", () ->
                                get(() -> {
                                    String csv = "\"id\",\"title\",\"subTitle\",\"mainStat\",\"secondaryStat\",\"arc__success\",\"arc__errors\"\n" +
                                            "0,root,client,0.263,0.448,1,0\n" +
                                            "1,service:1,service,0.611,0.902,0.433,0.567\n" +
                                            "2,service:2,service,0.0387,0.791,0.832,0.168\n" +
                                            "3,service:3,service,0.688,0.111,0.818,0.182\n" +
                                            "4,service:4,service,0.188,0.897,0.394,0.606\n" +
                                            "5,service:5,service,0.568,0.170,0.431,0.569\n" +
                                            "6,service:6,service,0.837,0.556,0.206,0.794\n" +
                                            "7,service:7,service,0.695,0.00876,0.447,0.553\n" +
                                            "8,service:8,service,0.951,0.264,0.854,0.146\n" +
                                            "9,service:9,service,0.436,0.501,0.253,0.747";
                                    return complete(StatusCodes.OK, csv);
                                })
                        ),
                        path("edges", () ->
                                get(() -> {
                                    String csv = "\"id\",\"source\",\"target\"\n" +
                                            "0--1,0,1\n" +
                                            "0--2,0,2\n" +
                                            "0--6,0,6\n" +
                                            "1--3,1,3\n" +
                                            "1--7,1,7\n" +
                                            "1--1,1,1\n" +
                                            "2--4,2,4\n" +
                                            "2--5,2,5\n" +
                                            "2--8,2,8\n" +
                                            "2--2,2,2\n" +
                                            "3--3,3,3\n" +
                                            "4--4,4,4\n" +
                                            "5--9,5,9";
                                    return complete(StatusCodes.OK, csv);
                                })
                        )
                ))*/
        );
    }

    private void startHttp(int httpListenPort) throws IOException {
        http = Http.get(kvsSystem);
        binding = http.newServerAt("0.0.0.0", httpListenPort).bind(createRoute());

        System.out.println("Server online at http://0.0.0.0:" + httpListenPort);
//        System.in.read(); // let it run until user presses return

//        binding.thenCompose(ServerBinding::unbind) // trigger unbinding from the port
//                .thenAccept(unbound -> kvsSystem.terminate()); // and shutdown when done
    }

    public void exit(int returnCode) {
        if (null != binding) {
            binding.thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                    .thenAccept(unbound -> kvsSystem.terminate()); // and shutdown when done
        }
        if (null != http) {
            http.shutdownAllConnectionPools();
        }
        System.exit(returnCode);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionPOJO<K, V> {
        public final int id;
        public final K key;
        public final V value;

        public TransactionPOJO(Transaction transaction) {
            this(transaction.id, null, null);
        }

        @JsonCreator
        public TransactionPOJO(@JsonProperty("id") int id,
                               @JsonProperty("key") K key,
                               @JsonProperty("value") V value) {
            this.id = id;
            this.key = key;
            this.value = value;
        }
    }

    final ActorSystem<SpawnProtocol.Command> kvsSystem;
    final KeyValueStoreClient client;
    Http http;
    CompletionStage<ServerBinding> binding;
    final HashMap<Integer, Transaction> transactions;   // TODO: remove id on abort, commit?
    final JMXExporter jmxExporter;
}
