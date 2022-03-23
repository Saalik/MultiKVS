package Types;

import java.time.Instant;

public class Timestamps {
    Instant timestamp;

    public Timestamps() {
        timestamp = Instant.now();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Timestamps{" +
                "timestamp=" + timestamp +
                '}';
    }
}
