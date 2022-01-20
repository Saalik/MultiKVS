package Journal;

public class Record {
    public enum Type {
        BEGIN, PREPARE, COMMIT, ABORT, OPERATION,
    }
    private static int cptId = 0;
    private static final Object mutex = new Object();
    private String trId;
    private String dependency;
    private int recordId;
    private Type type;
    private Operation operation;

    public Record(String trId, String dependency, Type type) {
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

    public Record(String trId, String dependency, Type type, String key, int value) {
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

    public String getTrId() {
        return trId;
    }

    public String getDependency() {
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
