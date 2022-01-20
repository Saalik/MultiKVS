package Interfaces;

import Types.TransactionID;

public interface KVSClient {
    void startTransaction();

    void startTransaction(TransactionID dependency);

    void put(String key, int value);

    int get(String key);

    String commitTransaction();

    void abort();
}