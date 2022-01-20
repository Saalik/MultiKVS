package com.bmartin.kvs;

import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConsoleClient {

    private static class State {
        KeyValueStoreClient kvsClient = null;
        Transaction currentTransaction = null;
        Map<Integer, Transaction> currentTransactions = new HashMap<>();
    }

    private static class StateException extends RuntimeException {
        StateException(String s) {
            super(s);
        }
    }


    public static void main(String[] args) throws IOException {
        System.setProperty("org.jline.terminal.dumb", "true");

        State currentState = new State();

        // check if a connection can be established from passed arguments
        if(args.length == 3) {
            // eg: ./console hostname seedIp seedPort
            // eg: ./console 127.0.0.1 127.0.0.1 25520
            currentState.kvsClient = connect(args[0], args[1], args[2]);
        }

        TerminalBuilder builder = TerminalBuilder.builder();
        Completer completer = null;
        Parser parser = new DefaultParser();
        Terminal terminal = builder.build();

//        System.out.println(terminal.getName() + ": " + terminal.getType());
        System.out.println("\nhelp: list available commands");

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .parser(parser)
//                .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
                .variable(LineReader.INDENTATION, 2)
                .option(LineReader.Option.INSERT_BRACKET, true)
                .build();

        terminal.writer().println("-----------------------------");
        terminal.writer().println("\t KVS Console");
        terminal.writer().println("-----------------------------");
        terminal.flush();

        Executors.newScheduledThreadPool(1)
                .scheduleAtFixedRate(() -> {
                    try {
                        if (null == currentState.kvsClient) {
                            rightPrompt = "NOT connected";
                        } else {
                            rightPrompt = currentState.kvsClient.getConnectionInfo();
                        }
                        if (null != currentState.currentTransaction) {
                            rightPrompt += " | " + currentState.currentTransaction.id;
                        }
                        if (!currentState.currentTransactions.isEmpty()) {
                            rightPrompt += " | " + currentState.currentTransactions.keySet();
                        }


                        // Note: this is a hack !
                        ((LineReaderImpl) reader).setRightPrompt(rightPrompt);

                        reader.callWidget(LineReader.CLEAR);
//                        reader.getTerminal().writer().println("Hello world!");
                        reader.callWidget(LineReader.REDRAW_LINE);
                        reader.callWidget(LineReader.REDISPLAY);
                        reader.getTerminal().writer().flush();
                    } catch (IllegalStateException e) {
                        // Widgets can only be called during a `readLine` call
                    }
                }, 1, 1, TimeUnit.SECONDS);

        while (true) {
            String line = null;
            try {
                line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
                line = line.trim();

//                terminal.writer().println("======>\"" + line + "\"");
//                terminal.writer().println(
//                        AttributedString.fromAnsi("\u001B[33m======>\u001B[0m\"" + line + "\"")
//                                .toAnsi(terminal));
//                terminal.flush();

                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    exit(currentState, 0);
                    break;
                }
                ParsedLine pl = reader.getParser().parse(line, 0);

                if ("connect".equals(pl.word())) {
                    if (pl.words().size() == 4) {
                        currentState.kvsClient = connect(pl.words().get(1), pl.words().get(2), pl.words().get(3));
                    } else {
                        terminal.writer().println("Usage: connect <hostname> <ip> <port>");
                    }
                } else if ("disconnect".equals(pl.word())) {
                    if (null != currentState.kvsClient) {
                        // abort all current transactions and clear currentTransactions
                        for (Transaction t : currentState.currentTransactions.values()) {
                            currentState.kvsClient.abortTransaction(t);
                        }
                        currentState.currentTransactions.clear();

                        // disconnect
                        currentState.kvsClient.disconnect();

                        // set state to null
                        currentState.currentTransaction = null;
                        currentState.kvsClient = null;
                    }
                } else if ("begin".equals(pl.word())) {
                    checkState(currentState, true, false);

                    Transaction t = currentState.kvsClient.beginTransaction();
                    currentState.currentTransactions.put(t.id, t);
                    currentState.currentTransaction = t;

                    terminal.writer().println("OK. transaction id=" + t.id);
                } else if ("transaction".equals(pl.word())) {
                    checkState(currentState, true, true);

                    if (pl.words().size() == 2) {
                        Transaction t = currentState.currentTransactions.get(Integer.parseInt(pl.words().get(1)));
                        if (null == t) {
                            terminal.writer().println("unknown transaction");
                        } else {
                            currentState.currentTransaction = t;
                        }
                    } else {
                        terminal.writer().println("Usage: transaction <id>");
                    }
                } else if ("put".equals(pl.word())) {
                    checkState(currentState, true, true);

                    if (pl.words().size() == 3) {
                        currentState.kvsClient.put(currentState.currentTransaction, pl.words().get(1), pl.words().get(2));
                    } else {
                        terminal.writer().println("Usage: put <key> <value>");
                    }
                } else if ("get".equals(pl.word())) {
                    checkState(currentState, true, true);

                    if (pl.words().size() == 2) {
                        String value = currentState.kvsClient.get(currentState.currentTransaction, pl.words().get(1));
                        terminal.writer().println(value);
                    } else {
                        terminal.writer().println("Usage: get <key>");
                    }
                } else if ("commit".equals(pl.word())) {
                    checkState(currentState, true, true);
                    boolean res = currentState.kvsClient.commitTransaction(currentState.currentTransaction);
                    if(res) {
                        terminal.writer().println("OK");
                    } else {
                        terminal.writer().println("error");
                    }
                } else if ("abort".equals(pl.word())) {
                    checkState(currentState, true, true);
                    boolean res = currentState.kvsClient.abortTransaction(currentState.currentTransaction);
                    if(res) {
                        terminal.writer().println("OK");
                    } else {
                        terminal.writer().println("error");
                    }
                } else if ("clear".equals(pl.word())) {
                    terminal.puts(InfoCmp.Capability.clear_screen);
                } else if ("status".equals(pl.word())) {
                    if (null == currentState.kvsClient) {
                        terminal.writer().println("Not connected to KVS");
                    } else {
                        terminal.writer().println("Connected to " + currentState.kvsClient.getConnectionInfo());
                    }
                    if (null == currentState.currentTransaction) {
                        terminal.writer().println("Not currently in a transaction !");
                    } else {
                        terminal.writer().println("Current transaction id=" + currentState.currentTransaction.id);
                    }
                } else if ("help".equals(pl.word()) || "?".equals(pl.word())) {
                    help();
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (UserInterruptException | ExecutionException | InterruptedException e) {
                // Ignore
            } catch (EndOfFileException e) {
                return;
            } catch (StateException e) {
                terminal.writer().println(e.getMessage());
                terminal.flush();
            }
        }
    }

    public static void help() {
        String[] help = {
                "List of available commands:"
                , "  Builtin:"
                , "    connect      connect to KVS"
                , "    disconnect   disconnect from KVS"
                , "    begin        begin a trasaction"
                , "    transaction  change current trasaction"
                , "    put          insert a value in KVS"
                , "    get          retrieve a value from KVS"
                , "    commit       commit current transaction to KVS"
                , "    abort        abort operations from current transaction"
                , "    status       display status information"
                , "    exit|quit    exit"
                , "  Example:"
                , "    connect      connect 127.0.0.1 25520"
                , "    disconnect   disconnect"
                , "    begin        begin"
                , "    transaction  transaction 5"
                , "    put          put hello world"
                , "    get          get hello"
                , "    commit       commit"
                , "    abort        abort"
                , "    status       status"
                , "    exit|quit    exit"
                , "  Additional help:"
                , "    <command> --help"};
        for (String u : help) {
            System.out.println(u);
        }
    }

    public static void exit(State state, int statusCode) {
        if (null != state.kvsClient) {
            state.kvsClient.disconnect();
        }
        System.exit(statusCode);
    }

    public static KeyValueStoreClient connect(String hostname, String ip, String port) {
        assert (null != ip && !ip.isEmpty());
        assert (null != port && !port.isEmpty());

        Config config = ConfigFactory.parseString(
                "akka.cluster.jmx.multi-mbeans-in-same-jvm=on \n" +
                        "akka.remote.artery.canonical.hostname=" + hostname + "\n" +
                        "akka.remote.artery.canonical.port=0 \n" +
                        "akka.cluster.roles=[\"client\"] \n" +
                        "akka.cluster.seed-nodes=[\"akka://KVSSystem@" + ip + ":" + port + "\"] \n");

        ActorSystem<?> system = ActorSystem.create(KeyValueStoreActorSystem.create(),
                KeyValueStoreActorSystem.NAME,
                config.withFallback(ConfigFactory.load()));

        return new KeyValueStoreClient(system);
    }

    private static void checkState(State state, boolean client, boolean currentTransaction) {
        if (client && null == state.kvsClient) {
            throw new StateException("Not connected to KVS !");
        }

        if (currentTransaction && null == state.currentTransaction) {
            throw new StateException("Not currently in a transaction !");
        }
    }

    private static final String prompt = "\nkvs> ";
    private static String rightPrompt = "not connected";
}