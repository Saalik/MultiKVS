package Reference;

import PrimitiveType.Timestamp;
import PrimitiveType.TransactionID;

import java.util.HashMap;

public class KeyValueStore {

    HashMap<TransactionID, Transaction> backend;
    TransactionID lastTransactionID;

    public KeyValueStore() {
        backend = new HashMap<TransactionID, Transaction>();
        lastTransactionID = null;
    }

    public void commitTransaction(Transaction tr) {
        backend.put(tr.getId(), tr);
        lastTransactionID = tr.getId();
    }



    public TransactionID getLastTransactionID() {
        return lastTransactionID;
    }

    public Transaction getTransaction(Timestamp trID) {
        if (backend.containsKey(trID)) {
            return backend.get(trID);
        }else{
            return null;
        }
    }

    public Transaction removeTransaction(String trID) {
        if (backend.containsKey(trID)) {
            return backend.remove(trID);
        }else{
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (TransactionID key : backend.keySet()) {
            System.out.println(key + ": " + backend.get(key));
        }

        return "MemoryKVS.KeyValueStore{" +
                "kvs=" + backend +
                ", lastTransactionID='" + lastTransactionID + '\'' +
                '}';
    }
}
