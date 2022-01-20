package com.bmartin.kvs.journal;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Index that maps Keys K to Values V
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class ObjectIndex<K, V> extends AbstractIndex<K, V> {
    /**
     * Journal records
     */
    final Map<K, JournalActor.State.Record<K, V>> records;


    ObjectIndex(Map<Integer, Map<K, JournalActor.State.Record<K, V>>> liveRecords, Map<K, JournalActor.State.Record<K, V>> records) {
        super(liveRecords);
        this.records = Objects.requireNonNullElseGet(records, HashMap::new);
    }

    public ObjectIndex<K, V> lookup(JournalActor.Lookup<K, V> event) {
        System.out.println(">> ObjectIndex::lookup()");

        // first check if in liveRecords
        JournalActor.State.Record<K, V> record = null;
        Map<K, JournalActor.State.Record<K, V>> transactionRecords = liveRecords.get(event.transaction.id);
        if (null != transactionRecords) {
            record = transactionRecords.get(event.key);
        } else {
            // not in liveRecords, check records
            record = records.get(event.key);    // this can be null if key is not found
        }

        // reply to client
        event.replyTo.tell(new JournalActor.LookupReply<>(record));

        return this;
    }

    public ObjectIndex<K, V> commit(JournalActor.Commit event) {
        System.out.println(">> ObjectIndex::commit(): transactionId=" + event.transaction);

        Map<K, JournalActor.State.Record<K, V>> transactionRecords = liveRecords.get(event.transaction.id);

        // TODO: if failure, rollback ?
        if (null != transactionRecords) {
            for (Map.Entry<K, JournalActor.State.Record<K, V>> recordSet : transactionRecords.entrySet()) {
                // move record from liveRecords to records
                JournalActor.State.Record<K, V> record = recordSet.getValue();
                records.put(recordSet.getKey(), record);

                // update record
                record.type = JournalActor.State.Record.Type.COMMITTED;
                record.commitTime = event.commitTimeStamp;
            }

            liveRecords.remove(event.transaction.id);
            assert (!liveRecords.containsKey(event.transaction.id));

            // TODO: can this fail ?

//            assert (records.values().stream().anyMatch(e -> e.transaction == event.transaction && e.type == JournalActor.State.Record.Type.COMMITTED));

            // reply to client
            event.replyTo.tell(new JournalActor.CommitReply(true));
        } else {
            // no transaction with this id !
            event.replyTo.tell(new JournalActor.CommitReply(false));    // TODO: check this
        }

        return this;
    }

}