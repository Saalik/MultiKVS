package Journal;


import Types.TransactionID;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException {
        KeyValueStore kvs = new KeyValueStore();
        TransactionID lastTransactionID = null;
        Transaction tr = null;
        System.out.println("You can type help to get commands");

        while (true) {
            System.out.print("kvs $ ");
            Scanner in = new Scanner(System.in);
            //in.useDelimiter(" ");
            String str = in.nextLine();
            String[] cmds = str.split("\\s+");
            //System.out.println(cmds[0]);

            if (cmds[0].equals("echo")){
                for (int i = 1; i < cmds.length; i++){
                    System.out.print(cmds[i]+ " ");
                }
                System.out.println();
            }
            else if (cmds[0].equals("start")) {
                if (cmds.length == 1) {
                    if (lastTransactionID == null) {
                        tr = new Transaction(kvs);
                    } else {
                        tr = new Transaction(kvs, lastTransactionID);
                    }
                }
                else if (cmds.length == 2){
                    TransactionID depID = new TransactionID(cmds[1]);
                    if (kvs.getTransaction(depID) == true){
                        tr = new Transaction(kvs, depID);
                    }else{
                        System.out.println("MemoryKVS.Transaction does not exist ! Please retry with a correct transaction identifier");
                    }
                }
                else {
                    System.out.println("Argument error");
                }
            }
            else if (cmds[0].equals("get")){
                assert(tr != null);
                if (cmds.length == 2) {
                    System.out.println(tr.get(cmds[1]));
                }else {
                    System.out.println("Argument error");
                }
            }
            else if (cmds[0].equals("put")){
                assert (tr != null);
                if (cmds.length == 3) {
                    String key = cmds[1];
                    int value = Integer.parseInt(cmds[2]);
                    tr.put(key, value);
                    System.out.println(cmds[1] + ":" + cmds[2]);
                }else {
                    System.out.println("Argument error");
                }
            }
            else if (cmds[0].equals("commit")){
                assert (tr != null);
                if (cmds.length == 1){
                    if (tr.getOperations().isEmpty()){
                        System.out.println("Nothing to commit");
                    } else {
                        kvs.commitTransaction(tr);
                        lastTransactionID = tr.getId();
                        tr = null;
                    }
                }
            }
            else if (cmds[0].equals("abort")) {
                assert (tr == null);
                if (cmds.length == 1){
                    tr = null;
                }
            }
            else if (cmds[0].equals("help")){
                System.out.println(
                        "start: start transaction \n"
                                +"state: get the state of the Key Value Store\n"
                                +"put key value \n"
                                +"get key \n"
                                +"commit \n"
                                +"abort \n"
                                +"populate: populates Key Value Store\n"
                                +"dependency: returns the current dependency transaction ID \n"
                                +"help: list all the available commands \n"
                );
            }
            else if (cmds[0].equals("state")) {
                System.out.println(kvs.toString());
            }
            else if (cmds[0].equals("dependency")) {
                assert tr != null;
                System.out.println(tr.getDependency());
            }
            else if (cmds[0].equals("populate")) {
                if (lastTransactionID == null) {
                    tr = new Transaction(kvs);
                }else {
                    tr = new Transaction(kvs, lastTransactionID);
                }
                tr.put("hello", 3);
                tr.put("hello", 4);
                kvs.commitTransaction(tr);
                lastTransactionID = tr.getId();
                if (lastTransactionID == null) {
                    tr = new Transaction(kvs);
                }else {
                    tr = new Transaction(kvs, lastTransactionID);
                }
                tr.get("hello");

            }
            else {
                System.out.println("Command error");
            }

        }
    }
}
