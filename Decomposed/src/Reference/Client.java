package Reference;

import Interfaces.KVSClient;

public class Client extends Thread implements KVSClient {

    private String dependency;
    private String lastTransactionID;
    private Transaction tr;
    private KeyValueStore kvs;


    public Client(KeyValueStore kvs) {
        this.kvs = kvs;
        dependency = kvs.getLastTransactionID();
        tr = null;
        lastTransactionID = dependency;
    }

    public void run () {

    }

    public void startTransaction () {
        if (tr == null) {
            if (lastTransactionID == null) {
                tr = new Transaction(kvs);
            } else {
                tr = new Transaction(kvs, lastTransactionID);
            }
        }
        else {
            assert(false);
        }
    }

    public void startTransaction(String dependency) {
        if (tr == null) {
            if (kvs.getTransaction(dependency) != null){
                tr = new Transaction(kvs, dependency);
            }else{
                System.out.println("MemoryKVS.Transaction does not exist ! Please retry with a correct transaction identifier");
            }
        }
        else {
            assert(false);
        }
    }

    public void put(String key, int value){
        assert (tr != null);
        tr.put(key, value);
    }

    public int get(String key){
        assert(tr != null);
        return tr.get(key);
    }

    public String commitTransaction (){
        assert (tr != null);
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

    public void abort() {
        assert (tr != null);
        tr = null;
    }

}
