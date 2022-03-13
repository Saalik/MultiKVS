package Interfaces;

import Types.TransactionID;

public interface KVSClient {



    /** Precondition:
     *  The snapshot is valid in regards to the consistency model
     */
    void startTransaction();

    /** Precondition:
     *  The snapshot is valid in regards to the consistency model
     */
    void startTransaction(TransactionID dependency);


    void effect(String key, int value);

    int get(String key);

    TransactionID commitTransaction();

    void abort();
}