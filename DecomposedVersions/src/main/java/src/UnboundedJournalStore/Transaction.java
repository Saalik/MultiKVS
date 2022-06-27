package UnboundedJournalStore;




import BoundedStore.Value;
import PrimitiveType.TransactionID;

import java.util.ArrayList;
import java.util.UUID;

public class Transaction {

    private TransactionID transactionID;
    private TransactionID dependency;
    private ArrayList<Operation> operations;
    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        transactionID = new TransactionID(UUID.randomUUID().toString());
        dependency = kvs.lastTransactionID;;
        operations = new ArrayList<>();
        this.kvs = kvs;
        kvs.put(new Record(transactionID, dependency, Record.Type.BEGIN));
    }

    public Transaction(KeyValueStore kvs, TransactionID dependency) {
        transactionID = new TransactionID(UUID.randomUUID().toString());
        this.dependency = dependency;
        operations = new ArrayList<>();
        this.kvs = kvs;
        kvs.put(new Record(transactionID, dependency, Record.Type.BEGIN));
    }

    public void put(String key, int value){
        kvs.put(new Record(transactionID, dependency, Record.Type.OPERATION, key, value));
        operations.add(new Operation(key,value));

    }

    public int get(String key){
        int counter = 0;
        for (Operation ope: operations) {
            if (ope.getKey().equals(key)) {
                counter = counter + ope.getValue();
            }
        }

        return counter + kvs.lookup(key, dependency);
    }

    public ArrayList<Operation> getOperations(){
        return operations;
    }

    public TransactionID getTransactionID() {
        return transactionID;
    }

    public TransactionID getDependency() {
        return dependency;
    }

}
