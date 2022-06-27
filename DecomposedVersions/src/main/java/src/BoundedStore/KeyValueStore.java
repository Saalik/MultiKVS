package BoundedStore;

import PrimitiveType.Timestamp;
import PrimitiveType.TransactionID;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import java.util.HashMap;

public class KeyValueStore {
    static final int MLIMIT = 100;

    TransactionID lastTransactionID;
    Multimap<String, Value> backend;
    MutableGraph<TransactionID> dependencyGraph;

    Timestamp minDependency;



    public KeyValueStore() {
        backend = HashMultimap.create();
        dependencyGraph = GraphBuilder.directed().build();
        minDependency = null;
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

    public Value getValue (String key, TransactionID transactionID) {
        boolean stopSearch = false;

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

    public TransactionID getLastTransactionID() {
        return lastTransactionID;
    }

    public boolean transactionIDExist(TransactionID transactionID) {
        return dependencyGraph.nodes().contains(transactionID);
    }

    public boolean sizeAvailable() {
        int mUsed = backend.size();
        return mUsed <= MLIMIT;
    }

}
