package Reference;

import PrimitiveType.TransactionID;

import java.util.HashMap;
import java.util.UUID;

public class Transaction {

    private TransactionID id;
    private TransactionID dependency;
    private HashMap<String, Integer> operations;
    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = new TransactionID(UUID.randomUUID().toString());
        dependency = kvs.lastTransactionID;;
        operations = new HashMap<String, Integer>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, TransactionID dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        operations = new HashMap<String, Integer>();
        this.kvs = kvs;
    }

    public void put(String key, int value){
        operations.put(key,value);
    }

    public int get(String key){
        int counter = 0;
        if (operations.containsKey(key)) {
            if (dependency == null){
                return operations.get(key);
            } else {
                Transaction dep = kvs.getTransaction(dependency);
                return operations.get(key) + dep.get(key);
            }
        }else{
            if (dependency == null){
                return counter;
            } else {
                Transaction dep = kvs.getTransaction(dependency);
                return dep.get(key);
            }
        }
    }


    public TransactionID getId() {
        return id;
    }

    public TransactionID getDependency() {
        return dependency;
    }

    public HashMap<String, Integer> getOperations() {
        return operations;
    }
}
