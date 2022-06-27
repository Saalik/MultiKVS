package UnboundedReference;
import Interfaces.KVSClient;
import PrimitiveType.*;
import static com.google.common.base.Preconditions.checkArgument;


public class Client extends Thread implements KVSClient {
    private Timestamp dependency;
    private Timestamp lastCommit;
    private Transaction tr;
    private KeyValueStore kvs;

    public Client (KeyValueStore kvs) {
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

    }

    @Override
    public void effect(Key key, int value) {

    }

    @Override
    public int read(Key key) {
        return 0;
    }


    public void beginTransaction(TransactionID transactionID) {
        checkArgument(tr == null, "Transaction already started");
        if (kvs.dependencyIsValid(dependency)){
            tr = new Transaction(kvs, dependency);
        }else{
            System.out.println("Dependency is not valid ! " +
                    "Please retry with a correct transaction identifier");
        }
    }

    public void effect(String key, int value){
        checkArgument(tr != null, "Transaction not started");
/*        tr.effect(key, value);*/
    }


    public int get(String key){
        checkArgument(tr != null, "Transaction not started");
        return tr.get(key);
    }

    @Override
    public TransactionID commitTransaction(){
        checkArgument(tr != null, "Transaction not started");
/*        if (tr.getEffectMap().isEmpty()){
            System.out.println("Nothing to commit");
            return null;
        } else {
            kvs.commitTransaction(tr);
            lastCommit = tr.getId();
            tr = null;
            return lastCommit;
        }*/
        return null;
    }

    @Override
    public void abort() {
        checkArgument(tr != null, "Transaction not started");
        tr = null;
    }

    @Override
    public void crash() {

    }

}
