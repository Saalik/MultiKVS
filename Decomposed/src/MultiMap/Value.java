package MultiMap;

public class Value {
    private int value;
    private String transactionId;
    // TODO: Change this into a Hashmap
    public Value(String transactionId, int value) {

        this.value = value;
        this.transactionId = transactionId;
        assert(false);
    }

    public int getValue() {
        return value;
    }

    public String getTransactionID() {
        return transactionId;
    }

}
