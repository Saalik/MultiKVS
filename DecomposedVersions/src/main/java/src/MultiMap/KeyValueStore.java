package MultiMap;

import Types.Timestamps;
import Types.TransactionID;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import java.util.HashMap;

public class KeyValueStore {
    Timestamps lastTransactionTimestamp;
    Multimap<String, Value> backend;
    MutableGraph<TransactionID> dependencyGraph;


    public KeyValueStore() {
        backend = HashMultimap.create();
        dependencyGraph = GraphBuilder.directed().build();
        lastTransactionTimestamp = null;
    }

    public void commitTransaction (Transaction tr) {
        dependencyGraph.addNode(tr.getId());
        if (tr.getDependency() != null) {
            dependencyGraph.putEdge(tr.getDependency(), tr.getId());
        }

        HashMap<String, Value> operations = tr.getEffectMap();

        for (String key : operations.keySet()) {
            backend.put(key, operations.get(key));
        }

    }

    public Value getValue (String key, Timestamps dependency) {
        boolean stopSearch = false;
        Timestamps dependencyTimestamp = dependency;

        TransactionID trID = transactionID;
        if (backend.containsKey(key)) {
            while ( ! stopSearch ) {
                for (Value value : backend.get(key)) {
                    if (value.getTransactionID().equals(trID)) {
                        return value;
                    }
                }
                if (dependencyGraph.predecessors(trID).size() == 1) {
                    trID = dependencyGraph.predecessors(trID).iterator().next();
                } else if (dependencyGraph.predecessors(trID).size() == 0) {
                    stopSearch = true;
                } else {
                    assert false;
                }
            }
        }

        return null;
    }

    public Timestamps getLastTransactionTimestamp() {
        return lastTransactionTimestamp;
    }

    public boolean dependencyIsValid(TransactionID transactionID) {
        return dependencyGraph.nodes().contains(transactionID);
    }

}
