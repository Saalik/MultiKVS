package Reference;

import java.util.HashMap;
import java.util.UUID;

public class Transaction {

    private String id;
    private String dependency;
    private HashMap<String, Integer> operations;
    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = UUID.randomUUID().toString();;
        dependency = kvs.lastTransactionID;;
        operations = new HashMap<String, Integer>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, String dependency) {
        id = UUID.randomUUID().toString();;
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


    public String getId() {
        return id;
    }

    public String getDependency() {
        return dependency;
    }

    public HashMap<String, Integer> getOperations() {
        return operations;
    }
}
