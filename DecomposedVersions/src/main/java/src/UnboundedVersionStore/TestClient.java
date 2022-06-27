package UnboundedVersionStore;


import PrimitiveType.Key;
import PrimitiveType.Timestamp;

public class TestClient {
    public static void main(String[] args) {
        KeyValueStore kvs = new KeyValueStore();
        Client client = new Client(kvs);
        Timestamp dependency1;
        Timestamp dependency2;
        Timestamp dependency3;
        Key key1 = new Key("key1");
        Key key2 = new Key("key1");
        Key key3 = new Key("key3");
        Key key4 = new Key("key4");
        Key key5 = new Key("key5");
        int effect1 = 1;
        int effect2 = 2;
        int effect3 = 3;
        client.start();

        System.out.println("Test 1: Add a key-value pair");
        client.beginTransaction();
        client.effect(key1, 1);
        client.effect(key2, 2);
        client.effect(key3, 3);
        assertEqual(client.read(key1), effect1);
        assertEqual(client.read(key2), effect2);
        assertEqual(client.read(key3), effect3);
        dependency1 = client.commitTransaction();

        System.out.println("Test 1 passed");

        System.out.println("Test 2: Add a key-value pair and then read new value");
        client.beginTransaction();
        assertEqual(client.read(key1), effect1);
        effect1 += 4;
        client.effect(key1, 4);
        assertEqual(client.read(key1), effect1);
        assertEqual(client.read(key2), effect2);
        assertEqual(client.read(key3), effect3);
        dependency2 = client.commitTransaction();

        System.out.println("Test 2 passed");

        System.out.println("Test 3: Add a key-value pair and read from an older dependency");
        client.beginTransaction();
        assertEqual(client.read(key1), effect1);
        effect1 += 4;
        client.effect(key1, 4);
        assertEqual(client.read(key1), effect1);
        assertEqual(client.read(key2), effect2);
        assertEqual(client.read(key3), effect3);
        dependency3 = client.commitTransaction();

        client.beginTransaction(dependency2);
        assertNotEqual(client.read(key1), effect1);
        client.commitTransaction();
        System.out.println("Test 3 passed");


        System.out.println("Test 4: Writing a 100 times in a single transaction");

        client.beginTransaction();
        for (int i= 0; i < 100 ; i++) {
            client.effect(key4, 1);
        }
        Timestamp dependency4 = client.commitTransaction();

        client.beginTransaction(dependency4);
        assertEqual(client.read(key4), 100);
        client.abort();

        System.out.println("Test 4 passed");

        System.out.println("Test 5: Writing a 100 times in different transactions");

        Timestamp dependency5 = null;
        for (int i= 0; i < 100 ; i++) {
            client.beginTransaction();
            client.effect(key5, 1);
            dependency5 = client.commitTransaction();
        }

        client.beginTransaction(dependency5);
        assertEqual(client.read(key5), 100);
        client.abort();

        System.out.println("Test 5 passed");




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
