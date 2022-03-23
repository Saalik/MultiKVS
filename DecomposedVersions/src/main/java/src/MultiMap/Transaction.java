package MultiMap;

import Types.Timestamps;
import Types.TransactionID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class Transaction {
    // Unique by definition
    private TransactionID id;
    // Identifies the transactions this one depends upon
    private Timestamps dependency;
    // records the content of the transaction’s writes
    private HashMap<String, Value> effectMap;
    // records what objects the transaction has read
    private CopyOnWriteArraySet<String> readSet;
    // time of commit
    private Timestamps commit;

    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = new TransactionID(UUID.randomUUID().toString());
        effectMap = new HashMap<>();
        readSet = new CopyOnWriteArraySet<>();
        dependency = kvs.getLastTransactionID();;
        effectMap = new HashMap<>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, TransactionID dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        effectMap = new HashMap<>();
        this.kvs = kvs;
    }


    //
    public void effect(String key, int value) {
        Value newValue = null;

        if (effectMap.containsKey(key)) {
            Value oldValue = effectMap.get(key);
            newValue = new Value(id, oldValue.getValue() + value);
            effectMap.put(key, newValue);
        } else {
            Value oldVal = kvs.getValue(key, dependency);
            if (oldVal != null) {
                newValue = new Value(id, value + oldVal.getValue());
                effectMap.put(key, newValue);
            } else {
                effectMap.put(key, new Value(id, value));
            }
        }
    }

    // TODO : This might return a null value
    public int get(String key) {
        Value value = effectMap.get(key);
        if (value != null) {
            return value.getValue();
        } else {
            return kvs.getValue(key, dependency).getValue();
        }
    }

    public TransactionID getId() {
        return id;
    }

    public TransactionID getDependency() {
        return dependency;
    }

    public HashMap<String, Value> getEffectMap() {
        return effectMap;
    }
}
