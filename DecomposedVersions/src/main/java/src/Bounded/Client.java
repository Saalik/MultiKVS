package Bounded;
import Interfaces.KVSClient;
import Types.TransactionID;

import static com.google.common.base.Preconditions.checkArgument;


public class Client extends Thread implements KVSClient {
    private TransactionID dependency;
    private TransactionID lastTransactionID;
    private Bounded.Transaction tr;
    private Bounded.KeyValueStore kvs;

    public Client (Bounded.KeyValueStore kvs) {
        this.kvs = kvs;
        dependency = kvs.getLastTransactionID();
        tr = null;
        lastTransactionID = dependency;
    }

    public void run () {}

    @Override
    public void startTransaction() {
        checkArgument(tr == null, "Transaction already started");
        if (lastTransactionID == null) {
            tr = new Bounded.Transaction(kvs);
        } else {
            tr = new Bounded.Transaction(kvs, lastTransactionID);
        }

    }

    @Override
    public void startTransaction(TransactionID dependency) {
        checkArgument(tr == null, "Transaction already started");
        if (kvs.transactionIDExist(dependency)){
            tr = new Bounded.Transaction(kvs, dependency);
        }else{
            System.out.println("MemoryKVS.Transaction does not exist ! Please retry with a correct transaction identifier");
        }
    }


    @Override
    public void effect(String key, int value){
        checkArgument(tr != null, "Transaction not started");
        tr.put(key, value);
    }

    @Override
    public int get(String key){
        checkArgument(tr != null, "Transaction not started");
        return tr.get(key);
    }

    @Override
    public TransactionID commitTransaction(){
        checkArgument(tr != null, "Transaction not started");
        if (tr.getOperations().isEmpty()){
            System.out.println("Nothing to commit");
            return null;
        } else {
            kvs.commitTransaction(tr);
            lastTransactionID = tr.getId();
            tr = null;
            return lastTransactionID;
        }
    }

    @Override
    public void abort() {
        checkArgument(tr != null, "Transaction not started");
        tr = null;
    }

}
