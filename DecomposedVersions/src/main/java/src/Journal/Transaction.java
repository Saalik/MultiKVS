package Journal;




import Types.TransactionID;

import java.util.ArrayList;
import java.util.UUID;

public class Transaction {

    private TransactionID id;
    private TransactionID dependency;
    private ArrayList<Operation> operations;
    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = new TransactionID(UUID.randomUUID().toString());
        dependency = kvs.lastTransactionID;;
        operations = new ArrayList<>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, TransactionID dependency) {
        id = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        operations = new ArrayList<>();
        this.kvs = kvs;
    }

    public void put(String key, int value){
        operations.add(new Operation(key,value));
    }

    public int get(String key){
        int counter = 0;
        for (Operation ope: operations) {
            if (ope.getKey().equals(key)) {
                counter = counter + ope.getValue();
            }
        }

        return counter + kvs.getKey(key, dependency);
    }

    public ArrayList<Operation> getOperations(){
        return operations;
    }

    public TransactionID getId() {
        return id;
    }

    public TransactionID getDependency() {
        return dependency;
    }

}
