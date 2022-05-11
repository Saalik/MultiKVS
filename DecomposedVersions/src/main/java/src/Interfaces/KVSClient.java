package Interfaces;

import Bounded.Value;
import Types.TransactionID;

public interface KVSClient {



    /** Precondition:
     *  The snapshot is valid in regards to the consistency model
     */
    void beginTransaction();

    /** Precondition:
     *  The snapshot is valid in regards to the consistency model
     */
    void beginTransaction(TransactionID dependency);


    void effect(String key, Value value);

    int get(String key);


    TransactionID commitTransaction();

    void abort();
}