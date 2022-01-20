package Types;

import MultiMap.Transaction;

public class TransactionID{

    private final String trID;

    public TransactionID (String str) {
        trID = str;
    }

    public String getTrID() {
        return trID;
    }


}
