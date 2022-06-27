package UnboundedJournalStore;

import PrimitiveType.TransactionID;

import java.io.*;
import java.util.ArrayList;

public class KeyValueStore  {
    // Here the Backend is a Journal
    ArrayList<Record> backend;
    TransactionID lastTransactionID;
    String JOURNAL = "UnboundedJournal";

    public KeyValueStore() {
        if (!recovery()){
            System.exit(1);
        }
        lastTransactionID = null;
    }

    public synchronized void put(Record record) {
        try {
            FileOutputStream fos = new FileOutputStream(JOURNAL);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(record);
            oos.close();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void commit(Transaction tr) {
        TransactionID trId = tr.getTransactionID();
        TransactionID dependency = tr.getDependency();
        backend.add(new Record(tr.getTransactionID(), tr.getDependency(), Record.Type.BEGIN));
        for (Operation ope : tr.getOperations()){
            //System.out.println("Adding "+ope.toString());
            backend.add(new Record(trId, dependency, Record.Type.OPERATION, ope.getKey(), ope.getValue()));
        }
        Record commitRecord = new Record(tr.getTransactionID(), tr.getDependency(), Record.Type.COMMIT);
        synchronized (this) {
            try {
                FileOutputStream fos = new FileOutputStream(JOURNAL);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(commitRecord);
                oos.close();
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        backend.add(commitRecord);
        lastTransactionID = tr.getTransactionID();
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

    public int lookup(String key, TransactionID dependency) {
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

    public boolean recovery () {
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = new FileInputStream(JOURNAL);
            ois = new ObjectInputStream(fis);
            backend = (ArrayList<Record>) ois.readObject();
            ois.close();
            fis.close();
            return true;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }


    }
}
