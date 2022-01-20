package MultiMap;

import Types.TransactionID;

import java.util.HashMap;
import java.util.UUID;

public class Transaction {
        private TransactionID id;
        private TransactionID dependency;
        private HashMap<String, Value> operations;
        private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = new TransactionID(UUID.randomUUID().toString());
        dependency = kvs.getLastTransactionID();;
        operations = new HashMap<>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, TransactionID dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        operations = new HashMap<>();
        this.kvs = kvs;
    }

    public void put(String key, int value) {
        Value newValue = null;

        if (operations.containsKey(key)) {
            Value oldValue = operations.get(key);
            newValue = new Value(id, oldValue.getValue() + value);
            operations.put(key, newValue);
        } else {
            Value oldVal = kvs.getValue(key, dependency);
            if (oldVal != null) {
                newValue = new Value(id, value + oldVal.getValue());
                operations.put(key, newValue);
            } else {
                operations.put(key, new Value(id, value));
            }
        }
    }

    // TODO : This might return a null value
    public int get(String key) {
        Value value = operations.get(key);
        if (value != null) {
            return value.getValue();
        } else {
            return kvs.getValue(key, dependency).getValue();
        }
    }

    public String getId() {
        return id;
    }

    public String getDependency() {
        return dependency;
    }

    public HashMap<String, Value> getOperations() {
        return operations;
    }
}
