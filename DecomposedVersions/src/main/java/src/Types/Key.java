package Types;

public class Key {
    String key;

    public Key (String str) {
        key = str;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return "Key{" +
                "key='" + key + '\'' +
                '}';
    }
}
