package Types;

import javax.annotation.Nullable;

public class ObjectVersions {
    private int value;
    private TransactionID transactionId;
    @Nullable
    private Timestamp commitTimestamp;

    public ObjectVersions(TransactionID transactionId, int value) {
        this.value = value;
        this.transactionId = transactionId;
        this.commitTimestamp = null;
        assert(false);
    }

    public int getValue() {
        return value;
    }

    public void setCommitTimestamp(@Nullable Timestamp commitTimestamp) {
        this.commitTimestamp = commitTimestamp;
    }

    @Nullable
    public Timestamp getCommitTimestamp() {
        return commitTimestamp;
    }

    /*
    @TODO
        Right now this merge function is basic
        ObjectVersions is supposed to be augmented with CRDTS
     */
    static public ObjectVersions merge(ObjectVersions one, ObjectVersions two) {
        return new ObjectVersions(two.getTransactionID(), one.getValue()+two.getValue());
    }

    public TransactionID getTransactionID() {
        return transactionId;
    }

}
