package Types;

import javax.annotation.Nullable;

public class Value {
    private int value;
    private TransactionID transactionId;
    @Nullable
    private Timestamp commitTimestamp;

    public Value(TransactionID transactionId, int value) {
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

    static public Value merge(Value one, Value two) {
        return new Value (two.getTransactionID(), one.getValue()+two.getValue());
    }

    public TransactionID getTransactionID() {
        return transactionId;
    }

}
