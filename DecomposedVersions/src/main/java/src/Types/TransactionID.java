package Types;

import java.util.UUID;

public class TransactionID{

    private final UUID trID;

    public TransactionID (String str) {
        trID = java.util.UUID.randomUUID();;
    }

    public UUID getTrID() {
        return trID;
    }


}
