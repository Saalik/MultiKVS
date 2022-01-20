package Reference;

public class TestClient {
    public static void main(String[] args) {
        KeyValueStore kvs = new KeyValueStore();
        Client client = new Client(kvs);
        String dependency1 = "";
        String dependency2 = "";
        String dependency3 = "";
        int key1 = 1;
        int key2 = 2;
        int key3 = 3;
        client.start();

        System.out.println("Test 1: Add a key-value pair");
        client.startTransaction();
        client.put("key1", 1);
        client.put("key2", 2);
        client.put("key3", 3);
        assertEqual(client.get("key1"), key1);
        assertEqual(client.get("key2"), key2);
        assertEqual(client.get("key3"), key3);
        dependency1 = client.commitTransaction();

        System.out.println("Test 1 passed");

        System.out.println("Test 2: Add a key-value pair and then read new value");
        client.startTransaction();
        assertEqual(client.get("key1"), key1);
        key1 += 4;
        client.put("key1", 4);
        assertEqual(client.get("key1"), key1);
        assertEqual(client.get("key2"), key2);
        assertEqual(client.get("key3"), key3);
        dependency2 = client.commitTransaction();

        System.out.println("Test 2 passed");

        System.out.println("Test 3: Add a key-value pair and read from an older dependency");
        client.startTransaction();
        assertEqual(client.get("key1"), key1);
        key1 += 4;
        client.put("key1", 4);
        assertEqual(client.get("key1"), key1);
        assertEqual(client.get("key2"), key2);
        assertEqual(client.get("key3"), key3);
        dependency3 = client.commitTransaction();

        client.startTransaction(dependency2);
        assertNotEqual(client.get("key1"), key1);
        client.commitTransaction();
        System.out.println("Test 3 passed");

        client.interrupt();
        try {
            client.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Journal: Test finished");

    }

    public static void assertEqual(int a, int b) {
        if (a != b) {
            throw new RuntimeException("Assertion failed");
        }
    }

    public static void assertNotEqual(int a, int b) {
        if (a == b) {
            throw new RuntimeException("Assertion failed");
        }
    }
}
