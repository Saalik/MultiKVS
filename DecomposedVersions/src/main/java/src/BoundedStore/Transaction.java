package BoundedStore;

import PrimitiveType.Timestamp;
import PrimitiveType.TransactionID;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.google.common.base.Preconditions.checkArgument;

public class Transaction {
    // Unique by definition
    private TransactionID id;
    // Identifies the transactions this one depends upon
    private Timestamp dependency;
    // records the content of the transaction’s writes
    private HashMap<String, Value> effectMap;
    // records what objects the transaction has read
    private CopyOnWriteArraySet<String> readSet;
    // time of commit
    private Timestamp commit;

    private KeyValueStore backend;

    public Transaction(KeyValueStore backend){
        id = new TransactionID(UUID.randomUUID().toString());
        dependency = backend.getLastTransactionID();;
        effectMap = new HashMap<>();
        this.backend = backend;
    }

    public Transaction(KeyValueStore backend, Timestamp dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        effectMap = new HashMap<>();
        this.backend = backend;
    }

    public void effect(String key, Value value) {
        checkArgument(backend.sizeAvailable(), "Memory size limit reached");

        @Nullable
        Value newValue = null;
        try {
            if (effectMap.containsKey(key)) {
                Value oldValue = effectMap.get(key);
                newValue = Value.merge(oldValue ,value);
                effectMap.put(key, newValue);
            } else {
                Value oldValue = backend.getValue(key, dependency);
                if (oldValue != null) {
                    newValue = Value.merge(oldValue ,value);
                    effectMap.put(key, newValue);
                } else {
                    effectMap.put(key, new Value(id, value));
                }
            }
        } finally {
            checkArgument(effectMap.containsValue(newValue), "ObjectVersions was not added to effectMap");
        }
    }
    

    public int get(String key) {
        Value value = effectMap.get(key);
        if (value != null) {
            return value.getValue();
        } else {
            return backend.getValue(key, dependency).getValue();
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
