package Journal;

public class Operation {
    private final String key;
    private final int value;

    public Operation(String key, int value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Operation{" +
                "key='" + key + '\'' +
                ", value=" + value +
                '}';
    }
}
