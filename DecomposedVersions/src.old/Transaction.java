import java.util.HashMap;
import java.util.UUID;

public class Transaction {

    private String id;
    private String dependency;
    private HashMap<String, String> operations;
    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = UUID.randomUUID().toString();;
        dependency = kvs.lastTransactionID;;
        operations = new HashMap<String, String>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, String dependency) {
        id = UUID.randomUUID().toString();;
        this.dependency = dependency;
        operations = new HashMap<String, String>();
        this.kvs = kvs;
    }

    public void put(String key, String value){
        operations.put(key,value);
    }

    public String get(String key){
        if (operations.containsKey(key)) {
            return operations.get(key);
        }else{
            if (dependency == null){
                return null;
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

    public HashMap<String, String> getOperations() {
        return operations;
    }
}
