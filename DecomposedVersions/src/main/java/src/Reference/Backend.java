package Reference;

public interface Backend {
    public void put(String key, String value);
    public void get(String key);
    public void commitTransaction (Transaction tr);


}
