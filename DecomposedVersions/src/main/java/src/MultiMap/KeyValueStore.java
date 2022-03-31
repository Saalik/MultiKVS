package MultiMap;

import Types.Timestamp;
import Types.TransactionID;
import Types.Value;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {
    Timestamp lastTransactionTimestamp;
    Multimap<String, Value> backend;
    MutableGraph<Timestamp> dependencyGraph;
    ConcurrentHashMap<Timestamp, TransactionID> index;


    public KeyValueStore() {
        backend = HashMultimap.create();
        dependencyGraph = GraphBuilder.directed().build();
        lastTransactionTimestamp = null;
    }

    public void commitTransaction (Transaction transaction) {
        transaction.setCommit(new Timestamp());
        index.put(transaction.getCommit(), transaction.getId());

        dependencyGraph.addNode(transaction.getCommit());

        if (transaction.getDependency() != null) {
            dependencyGraph.putEdge(transaction.getDependency(), transaction.getCommit());
        }

        HashMap<String, Value> operations = transaction.getEffectMap();

        for (String key : operations.keySet()) {
            backend.put(key, operations.get(key));
        }
    }

    public Value getValue (String key, Timestamp dependency) {
        boolean stopSearch = false;
        Timestamp dependencyTimestamp = dependency;
        TransactionID trID = index.get(dependency);

        if (backend.containsKey(key)) {
            while ( ! stopSearch ) {
                for (Value value : backend.get(key)) {
                    if (value.getTransactionID().equals(trID)) {
                        return value;
                    }
                }
                if (dependencyGraph.predecessors(dependency).size() == 1) {
                    dependencyTimestamp = dependencyGraph.predecessors(dependency).iterator().next();
                    trID = index.get(dependencyTimestamp);

                } else if (dependencyGraph.predecessors(dependency).size() == 0) {
                    stopSearch = true;
                } else {
                    assert false;
                }
            }
        }

        return null;
    }

    public Timestamp getLastTransactionTimestamp() {
        return lastTransactionTimestamp;
    }

    public boolean dependencyIsValid(TransactionID transactionID) {
        return dependencyGraph.nodes().contains(transactionID);
    }

}
