package Journal;



import Types.TransactionID;

import java.util.ArrayList;

public class KeyValueStore {
    // Here the Backend is a Journal
    ArrayList<Record> backend;
    TransactionID lastTransactionID;

    public KeyValueStore() {
        backend = new ArrayList<>();
        lastTransactionID = null;
    }

    public void commitTransaction(Transaction tr) {
        TransactionID trId = tr.getId();
        TransactionID dependency = tr.getDependency();
        backend.add(new Record(tr.getId(), tr.getDependency(), Record.Type.BEGIN));
        for (Operation ope : tr.getOperations()){
            //System.out.println("Adding "+ope.toString());
            backend.add(new Record(trId, dependency, Record.Type.OPERATION, ope.getKey(), ope.getValue()));
        }
        backend.add(new Record(tr.getId(), tr.getDependency(), Record.Type.COMMIT));
        lastTransactionID = tr.getId();
    }

    public TransactionID getLastTransactionID() {
        return lastTransactionID;
    }


    @Override
    public String toString() {
        for (Record rec : backend){
            System.out.println(rec.getType());
            if ( rec.getType() == Record.Type.OPERATION) {
                rec.getOperation().toString();
            }
        }
        return "MemoryKVS.KeyValueStore{" +
                "kvs=" + backend.toString() +
                ", lastTransactionID='" + lastTransactionID + '\'' +
                '}';
    }

    public int getKey(String key, TransactionID dependency) {
        boolean commit_seen = false;
        int counter = 0;
        for (int i = backend.size()-1; i > 0 ; i-- ) {
            Record rec = backend.get(i);
            Record.Type type = rec.getType();
            // A running transaction must not have as a dependency another running transaction
            // Every begin has a corresponding commit or abort
            if (type == Record.Type.COMMIT && rec.getTrId().equals(dependency)) {
                commit_seen = true;
            } else if (commit_seen && type == Record.Type.OPERATION
                    && rec.getOperation().getKey().equals(key) ) {
                // This is the last value affected to our key
                counter = counter + rec.getOperation().getValue();
                //System.out.println(counter+" "+rec.getOperation().getValue());

            } else if (type == Record.Type.BEGIN && rec.getTrId().equals(dependency)) {
                // haven't found value. Look at  previous transaction in chain
                dependency = rec.getDependency();
                commit_seen = false;
            }
        }
        return counter;
    }

    public boolean getTransaction(TransactionID dependency) {
        for (int i = backend.size()-1; i > 0 ; i-- ) {
            Record rec = backend.get(i);
            Record.Type type = rec.getType();
            if (type == Record.Type.COMMIT && rec.getTrId().equals(dependency)) {
                return true;
            }
        }
        return false;

    }
}
