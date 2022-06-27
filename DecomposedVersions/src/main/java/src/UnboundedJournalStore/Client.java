package UnboundedJournalStore;
import static com.google.common.base.Preconditions.checkArgument;

import PrimitiveType.*;

import java.io.IOException;

public class Client extends Thread {
    private TransactionID dependency;
    private TransactionID lastTransactionID;
    private Transaction tr;
    private final KeyValueStore kvs;

    public Client(KeyValueStore kvs) {
        this.kvs = kvs;
        dependency = kvs.getLastTransactionID();
        tr = null;
        lastTransactionID = dependency;
    }

    @Override
    public void run() {
        while (isInterrupted()) {

        }
    }

    /*
        - Starts transaction with any valid dependency
     */
    public void begin() {
        checkArgument(tr == null, "Transaction already started");
        if (lastTransactionID == null) {
            tr = new Transaction(kvs);
        } else {
            tr = new Transaction(kvs, lastTransactionID);
        }

    }

    /*
        - Starts transaction with specified dependency
     */
    public void begin(TransactionID dependency) {
        checkArgument(tr == null, "Transaction already started");
        if (kvs.getTransaction(dependency) == true){
            tr = new Transaction(kvs, dependency);
        }else{
            System.out.println("Dependency does not exist ! Please retry with a correct transaction identifier");
        }
    }


    public void effect(String key, int value){
        checkArgument(tr != null, "Transaction not started");
        tr.put(key, value);
    }

    public int read(String key){
        checkArgument(tr != null, "Transaction not started");
        return tr.get(key);
    }

    public TransactionID commit() {
        checkArgument(tr != null, "Transaction not started");
        if (tr.getOperations().isEmpty()){
            System.out.println("Nothing to commit");
            tr = null;
            return lastTransactionID;
        } else {
            kvs.commit(tr);
            lastTransactionID = tr.getTransactionID();
            tr = null;
            return lastTransactionID;
        }
    }

    public Boolean isRunning(){
        return  (tr !=null);
    }

    public void abort() {
        checkArgument(tr != null, "Transaction not started");
        tr = null;
    }

}
