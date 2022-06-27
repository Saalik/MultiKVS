package Interfaces;


import PrimitiveType.Timestamp;
import PrimitiveType.Key;
import PrimitiveType.TransactionID;

public interface KVSClient {



    /** Precondition:
     *  The snapshot is valid in regards to the consistency model
     */
    void beginTransaction();

    /** Precondition:
     *  The snapshot is valid in regards to the consistency model
     * @param dependency
     */
    void beginTransaction(Timestamp dependency);


    void effect(Key key, int value);

    int read(Key key);


    TransactionID commitTransaction();

    void abort();

    void crash();
}