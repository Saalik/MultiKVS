package Journal;


import Types.TransactionID;

public class Record {
    public enum Type {
        BEGIN, PREPARE, COMMIT, ABORT, OPERATION,
    }
    private static int cptId = 0;
    private static final Object mutex = new Object();
    private TransactionID trId;
    private TransactionID dependency;
    private int recordId;
    private Type type;
    private Operation operation;

    public Record(TransactionID trId, TransactionID dependency, Type type) {
        this.trId = trId;
        this.dependency = dependency;
        this.type = type;
        if (type == Type.OPERATION){
            assert(false);
        }
        synchronized (mutex) {
            recordId = cptId;
            cptId++;
        }
    }

    public Record(TransactionID trId, TransactionID dependency, Type type, String key, int value) {
        this.trId = trId;
        this.dependency = dependency;
        this.type = type;
        if (type != Type.OPERATION){
            assert(false);
        } else {
            operation = new Operation(key, value);
            synchronized (mutex) {
                recordId = cptId;
                cptId++;
            }
        }

    }

    public TransactionID getTrId() {
        return trId;
    }

    public TransactionID getDependency() {
        return dependency;
    }

    public int getRecordId() {
        return recordId;
    }

    public Type getType() {
        return type;
    }

    public Operation getOperation() {
        return operation;
    }
}
