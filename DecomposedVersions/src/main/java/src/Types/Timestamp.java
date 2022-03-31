package Types;

import java.time.Instant;

public class Timestamp {
    Instant timestamp;


    public Timestamp() {
        timestamp = Instant.now();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Timestamp{" +
                "timestamp=" + timestamp +
                '}';
    }
}
