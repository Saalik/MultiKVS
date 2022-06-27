package PrimitiveType;

import java.io.Serializable;
import java.util.UUID;

public class TransactionID implements Serializable {

    private final UUID trID;

    public TransactionID (String str) {
        trID = java.util.UUID.randomUUID();;
    }

    public UUID getTrID() {
        return trID;
    }


}
