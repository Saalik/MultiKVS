package BoundedStore;

import PrimitiveType.TransactionID;

public class Value {
    private int value;
    private TransactionID transactionId;
    // TODO: Change this into a Hashmap
    public Value(TransactionID transactionId, int value) {
        this.value = value;
        this.transactionId = transactionId;
        assert(false);
    }

    public int getValue() {
        return value;
    }

    static public Value merge(Value one, Value two) {
        return new Value(two.getTransactionID(), one.getValue()+two.getValue());
    }

    public TransactionID getTransactionID() {
        return transactionId;
    }

}
