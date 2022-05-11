package UnboundedVersionStore;

import Interfaces.KVSClient;
import Types.Timestamp;
import Types.TransactionID;

import static com.google.common.base.Preconditions.checkArgument;


public class Client extends Thread implements KVSClient {
    private Timestamp dependency;
    private Timestamp lastCommit;
    private Transaction tr;
    private KeyValueStore kvs;

    public Client(KeyValueStore kvs) {
        this.kvs = kvs;
        dependency = kvs.getLastTransactionTimestamp();
        tr = null;
        lastCommit = dependency;
    }

    public void run () {}

    @Override
    public void beginTransaction() {
        checkArgument(tr == null, "Transaction already started");
        if (lastCommit == null) {
            tr = new Transaction(kvs);
        } else {
            tr = new Transaction(kvs, lastCommit);
        }

    }

    @Override
    public void beginTransaction(Timestamp dependency) {
        checkArgument(tr == null, "Transaction already started");
        if (kvs.dependencyIsValid(dependency)){
            tr = new Transaction(kvs, dependency);
        }else{
            System.out.println("Dependency is not valid ! " +
                    "Please retry with a correct transaction identifier");
        }
    }


    @Override
    public void effect(String key, int value){
        checkArgument(tr != null, "Transaction not started");
        tr.effect(key, value);
    }

    @Override
    public int get(String key){
        checkArgument(tr != null, "Transaction not started");
        return tr.get(key);
    }

    @Override
    public TransactionID commitTransaction(){
        checkArgument(tr != null, "Transaction not started");
        if (tr.getEffectMap().isEmpty()){
            System.out.println("Nothing to commit");
            return null;
        } else {
            kvs.commitTransaction(tr);
            lastCommit = tr.getId();
            tr = null;
            return lastCommit;
        }
    }

    @Override
    public void abort() {
        checkArgument(tr != null, "Transaction not started");
        tr = null;
    }

}
