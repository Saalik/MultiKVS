package Bounded;

import Types.TransactionID;

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

    public TransactionID getTransactionID() {
        return transactionId;
    }

}
