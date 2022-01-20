
public class Client implements Runnable {

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
        System.out.println("Client Started");
        while(true){

        }
    }

    public void startTransaction () {
        if (tr != null) {
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
        if (tr != null) {
            if (kvs.getTransaction(dependency) != null){
                tr = new Transaction(kvs, dependency);
            }else{
                System.out.println("Transaction does not exist ! Please retry with a correct transaction identifier");
            }
        }
        else {
            assert(false);
        }
    }

    public void put(String key, String value){
        assert (tr != null);
        tr.put(key, value);
    }

    public String get(String key){
        assert(tr != null);
        return tr.get(key);
    }

    public void commitTransaction (){
        assert (tr != null);
        if (tr.getOperations().isEmpty()){
            System.out.println("Nothing to commit");
        } else {
            kvs.commitTransaction(tr);
            lastTransactionID = tr.getId();
            tr = null;
        }
    }

    public void abort() {
        assert (tr != null);
        tr = null;
    }


}
