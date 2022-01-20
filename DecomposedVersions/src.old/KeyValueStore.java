import java.util.HashMap;

public class KeyValueStore {

    HashMap<String, Transaction> backend;
    String lastTransactionID;

    public KeyValueStore() {
        backend = new HashMap<String, Transaction>();
        lastTransactionID = null;
    }

    public void commitTransaction(Transaction tr) {
        backend.put(tr.getId(), tr);
        lastTransactionID = tr.getId();
    }

    public String getLastTransactionID() {
        return lastTransactionID;
    }

    public Transaction getTransaction(String trID) {
        if (backend.containsKey(trID)) {
            return backend.get(trID);
        }else{
            return null;
        }
    }

    @Override
    public String toString() {
        return "KeyValueStore{" +
                "kvs=" + backend.keySet() +
                '}';
    }
}
