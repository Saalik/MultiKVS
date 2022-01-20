package Journal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Transaction {

    private String id;
    private String dependency;
    private ArrayList<Operation> operations;
    private KeyValueStore kvs;

    public Transaction(KeyValueStore kvs){
        id = UUID.randomUUID().toString();;
        dependency = kvs.lastTransactionID;;
        operations = new ArrayList<>();
        this.kvs = kvs;
    }

    public Transaction(KeyValueStore kvs, String dependency) {
        id = UUID.randomUUID().toString();;
        this.dependency = dependency;
        operations = new ArrayList<>();
        this.kvs = kvs;
    }

    public void put(String key, int value){
        operations.add(new Operation(key,value));
    }

    public int get(String key){
//        System.out.println("IN GET");
        int counter = 0;
        for (Operation ope: operations) {
//            System.out.println(ope.toString());
            if (ope.getKey().equals(key)) {
                counter = counter + ope.getValue();
//                System.out.println("AU SECOURS");
            }
        }

        return counter + kvs.getKey(key, dependency);
    }

    public ArrayList<Operation> getOperations(){
        return operations;
    }

    public String getId() {
        return id;
    }

    public String getDependency() {
        return dependency;
    }

}
