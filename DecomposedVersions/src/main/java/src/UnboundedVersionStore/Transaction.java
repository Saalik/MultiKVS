package UnboundedVersionStore;

import PrimitiveType.Key;
import PrimitiveType.Timestamp;
import PrimitiveType.TransactionID;
import PrimitiveType.ObjectVersions;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class Transaction {
    // Unique by definition
    private TransactionID id;
    // Identifies the transactions this one depends upon
    private Timestamp dependency;
    // records the content of the transaction’s writes
    private HashMap<Key, ObjectVersions> effectMap;
    // records what objects the transaction has read
    private CopyOnWriteArraySet<String> readSet;
    // time of commit
    private Timestamp commit;
    // @TODO Initialisation a +infini


    private KeyValueStore kvs;


    public Transaction(KeyValueStore kvs){
        id = new TransactionID(UUID.randomUUID().toString());
        commit = new Timestamp(Instant.MAX);
        dependency = kvs.getLastTransactionTimestamp()  ;;
        effectMap = new HashMap<>();
        readSet = new CopyOnWriteArraySet<>();
        effectMap = new HashMap<>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, Timestamp dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        effectMap = new HashMap<>();
        this.kvs = kvs;
    }

    public void setCommit(Timestamp commit) {
        this.commit = commit;
    }

    //
    public void effect(Key key, int value) {
        ObjectVersions newObjectVersions = null;

        if (effectMap.containsKey(key)) {
            ObjectVersions oldObjectVersions = effectMap.get(key);
            newObjectVersions = new ObjectVersions(id, oldObjectVersions.getValue() + value);
            effectMap.put(key, newObjectVersions);
        } else {
            ObjectVersions oldVal = kvs.getValue(key, dependency);
            if (oldVal != null) {
                newObjectVersions = new ObjectVersions(id, value + oldVal.getValue());
                effectMap.put(key, newObjectVersions);
            } else {
                effectMap.put(key, new ObjectVersions(id, value));
            }
        }
    }

    // TODO : This might return a null value
    public int get(Key key) {
        ObjectVersions objectVersions = effectMap.get(key);
        if (objectVersions != null) {
            return objectVersions.getValue();
        } else {
            return kvs.getValue(key, dependency).getValue();
        }
    }

    public TransactionID getId() {
        return id;
    }

    public CopyOnWriteArraySet<String> getReadSet() {
        return readSet;
    }

    public Timestamp getCommit() {
        return commit;
    }

    public Timestamp getDependency() {
        return dependency;
    }

    public HashMap<Key, ObjectVersions> getEffectMap() {
        return effectMap;
    }
}
