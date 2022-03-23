package Bounded;

import Types.TransactionID;

import java.util.HashMap;
import java.util.UUID;

public class Transaction {
        private TransactionID id;
        private TransactionID dependency;
        private HashMap<String, Bounded.Value> operations;
        private Bounded.KeyValueStore kvs;

    public Transaction(Bounded.KeyValueStore kvs){
        id = new TransactionID(UUID.randomUUID().toString());
        dependency = kvs.getLastTransactionID();;
        operations = new HashMap<>();
        this.kvs = kvs;
    }

    public Transaction(Bounded.KeyValueStore kvs, TransactionID dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        operations = new HashMap<>();
        this.kvs = kvs;
    }

    public void put(String key, int value) {
        Bounded.Value newValue = null;

        if (operations.containsKey(key)) {
            Bounded.Value oldValue = operations.get(key);
            newValue = new Bounded.Value(id, oldValue.getValue() + value);
            operations.put(key, newValue);
        } else {
            Bounded.Value oldVal = kvs.getValue(key, dependency);
            if (oldVal != null) {
                newValue = new Bounded.Value(id, value + oldVal.getValue());
                operations.put(key, newValue);
            } else {
                operations.put(key, new Bounded.Value(id, value));
            }
        }
    }

    // TODO : This might return a null value
    public int get(String key) {
        Bounded.Value value = operations.get(key);
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

    public HashMap<String, Bounded.Value> getOperations() {
        return operations;
    }
}
