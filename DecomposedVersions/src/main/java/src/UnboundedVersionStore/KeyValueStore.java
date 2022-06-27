package UnboundedVersionStore;

import PrimitiveType.Timestamp;
import PrimitiveType.TransactionID;
import PrimitiveType.ObjectVersions;
import PrimitiveType.Key;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Previous version of the store didn't implement the specification strictly
 * This version aims to implement the specs exactly as written
 */

public class KeyValueStore {
    Timestamp lastTransactionTimestamp;
    Multimap<Key, ObjectVersions> store;
    CopyOnWriteArraySet<TransactionID> commitedTransactions;

//    MutableGraph<Timestamp> dependencyGraph;
    ConcurrentHashMap<Timestamp, TransactionID> index;


    public KeyValueStore() {
        store = HashMultimap.create();
//        dependencyGraph = GraphBuilder.directed().build();
        lastTransactionTimestamp = null;
        commitedTransactions = new CopyOnWriteArraySet<>();
        index = new ConcurrentHashMap<>();
    }

    /* @TODO Commit timestamp should be uniq
         Rajouter un assert pour montrer que chaque commit timestamp.
    */
    public void commitTransaction (Transaction transaction) {

        transaction.setCommit(new Timestamp());
        HashMap<Key, ObjectVersions> operations = transaction.getEffectMap();
        for (Key key : operations.keySet()) {
            operations.get(key).setCommitTimestamp(transaction.getCommit());
            store.put(key, operations.get(key));
        }
        commitedTransactions.add(transaction.getId());
    }

    /**
     * getValue Returns if present the most recent version
     *
     *
     * @param key
     * @param dependency
     * @return
     */
    public ObjectVersions getValue (Key key, Timestamp dependency) {
        boolean stopSearch = false;
        Timestamp dependencyTimestamp = dependency;
        TransactionID trID = index.get(dependency);

        if (store.containsKey(key)) {
            while ( ! stopSearch ) {
                for (ObjectVersions objectVersions : store.get(key)) {
                    if (objectVersions.getTransactionID().equals(trID)) {
                        return objectVersions;
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

    public boolean dependencyIsValid(Timestamp dependency) {
        return dependencyGraph.nodes().contains(dependency);
    }

}
