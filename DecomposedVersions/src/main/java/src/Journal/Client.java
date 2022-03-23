package Journal;
import Interfaces.KVSClient;
import static com.google.common.base.Preconditions.checkArgument;

import Types.*;

public class Client extends Thread implements KVSClient {
    private TransactionID dependency;
    private TransactionID lastTransactionID;
    private Transaction tr;
    private KeyValueStore kvs;

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

    public void startTransaction () {
        checkArgument(tr == null, "Transaction already started");
        if (lastTransactionID == null) {
            tr = new Transaction(kvs);
        } else {
            tr = new Transaction(kvs, lastTransactionID);
        }

    }

    public void startTransaction(TransactionID dependency) {
        checkArgument(tr == null, "Transaction already started");
        if (kvs.getTransaction(dependency) == true){
            tr = new Transaction(kvs, dependency);
        }else{
            System.out.println("MemoryKVS.Transaction does not exist ! Please retry with a correct transaction identifier");
        }
    }


    public void effect(String key, int value){
        checkArgument(tr != null, "Transaction not started");
        tr.put(key, value);
    }

    public int get(String key){
        checkArgument(tr != null, "Transaction not started");
        return tr.get(key);
    }

    public TransactionID commitTransaction (){
        // CheckArgument == Assertion
        checkArgument(tr != null, "Transaction not started");
        if (tr.getOperations().isEmpty()){
            System.out.println("Nothing to commit");
            tr = null;
            return lastTransactionID;
        } else {
            kvs.commitTransaction(tr);
            lastTransactionID = tr.getId();
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
