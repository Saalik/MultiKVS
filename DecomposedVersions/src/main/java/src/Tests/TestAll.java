package Tests;

public class TestAll {
    public static void main(String[] args) {
        Reference.TestClient.main(args);
        UnboundedJournalStore.TestClient.main(args);
        UnboundedReference.TestClient.main(args);
    }
}
