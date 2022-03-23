package Bounded;

import Types.TransactionID;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import java.util.HashMap;

public class KeyValueStore {
    TransactionID lastTransactionID;
    Multimap<String, Bounded.Value> backend;
    MutableGraph<TransactionID> dependencyGraph;


    public KeyValueStore() {
        backend = HashMultimap.create();
        dependencyGraph = GraphBuilder.directed().build();
    }

    public void commitTransaction (Bounded.Transaction tr) {
        dependencyGraph.addNode(tr.getId());
        if (tr.getDependency() != null) {
            dependencyGraph.putEdge(tr.getDependency(), tr.getId());
        }

        HashMap<String, Bounded.Value> operations = tr.getOperations();

        for (String key : operations.keySet()) {
            backend.put(key, operations.get(key));
        }

    }

    public Bounded.Value getValue (String key, TransactionID transactionID) {
        boolean stopSearch = false;

        TransactionID trID = transactionID;
        if (backend.containsKey(key)) {
            while ( ! stopSearch ) {
                for (Bounded.Value value : backend.get(key)) {
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

}
