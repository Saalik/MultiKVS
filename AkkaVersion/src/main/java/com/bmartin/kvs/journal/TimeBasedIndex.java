package com.bmartin.kvs.journal;

import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public class TimeBasedIndex<K, V> extends AbstractIndex<K, V> {
    final TreeSet<JournalActor.State.Record<K, V>> records;

    TimeBasedIndex(Map<Integer, Map<K, JournalActor.State.Record<K, V>>> liveRecords, TreeSet<JournalActor.State.Record<K, V>> records) {
        super(liveRecords);
        this.records = Objects.requireNonNullElseGet(records, TreeSet::new);
    }

    @Override
    public TimeBasedIndex<K, V> lookup(JournalActor.Lookup<K, V> event) {
        System.out.println(">> TimeBasedIndex::lookup()");
        assert (null == event.key);
        // first check if in liveRecords
        Map<K, JournalActor.State.Record<K, V>> transactionRecords = liveRecords.get(event.transaction.id);

        if (JournalActor.Lookup.UNUSED == event.lowBound && JournalActor.Lookup.UNUSED == event.highBound) {
            // time based index not used
            return this;
        }

        JournalActor.State.Record<K, V> lowBoundRecord = null;
        JournalActor.State.Record<K, V> highBoundRecord = null;
        SortedSet<JournalActor.State.Record<K, V>> res = new TreeSet<>();

        /*
        1) lookup in live records
        2) lookup in records
         */
        if (JournalActor.Lookup.UNUSED != event.lowBound && JournalActor.Lookup.UNUSED == event.highBound) {
            // use only lowbound

            if (null != transactionRecords) {
                for (JournalActor.State.Record<K, V> r : transactionRecords.values()) {
                    if (r.timeStamp >= event.lowBound) {
                        res.add(r);
                    }
                }
            }

            lowBoundRecord = new JournalActor.State.Record<K, V>(event.lowBound);
            res.addAll(records.tailSet(lowBoundRecord));
        } else if (JournalActor.Lookup.UNUSED == event.lowBound && JournalActor.Lookup.UNUSED != event.highBound) {
            // use only highbound

            if (null != transactionRecords) {
                for (JournalActor.State.Record<K, V> r : transactionRecords.values()) {
                    if (r.timeStamp <= event.highBound) {
                        res.add(r);
                    }
                }
            }

            highBoundRecord = new JournalActor.State.Record<K, V>(event.highBound);
            res.addAll(records.headSet(highBoundRecord));
        } else {
            // use both lowbound and highbound

            if (null != transactionRecords) {
                for (JournalActor.State.Record<K, V> r : transactionRecords.values()) {
                    if (r.timeStamp >= event.lowBound) {
                        res.add(r);
                    }
                    if (r.timeStamp <= event.highBound) {
                        res.add(r);
                    }
                }
            }

            lowBoundRecord = new JournalActor.State.Record<K, V>(event.lowBound);
            highBoundRecord = new JournalActor.State.Record<K, V>(event.highBound);
            res.addAll(records.subSet(lowBoundRecord, highBoundRecord));
        }

        event.replyTo.tell(new JournalActor.LookupReply<>(res));

        return this;
    }

    @Override
    public TimeBasedIndex<K, V> commit(JournalActor.Commit event) {
        System.out.println(">> TimeBasedIndex::commit()");
        Map<K, JournalActor.State.Record<K, V>> transactionRecords = liveRecords.get(event.transaction.id);

        // TODO: if failure, rollback ?
        if (null != transactionRecords) {
            for (Map.Entry<K, JournalActor.State.Record<K, V>> recordSet : transactionRecords.entrySet()) {
                // move record from liveRecords to records
                JournalActor.State.Record<K, V> record = recordSet.getValue();
                records.add(record);

                // update record
                record.type = JournalActor.State.Record.Type.COMMITTED;
                record.commitTime = event.commitTimeStamp;
            }
            liveRecords.remove(event.transaction.id);
            assert (!liveRecords.containsKey(event.transaction.id));

            // TODO: can this fail ?

//                assert (records.values().stream().anyMatch(e -> e.transaction == event.transaction && e.type == Record.Type.COMMITTED));

            // reply to client
            event.replyTo.tell(new JournalActor.CommitReply(true));
        } else {
            event.replyTo.tell(new JournalActor.CommitReply(false));    // TODO: check this
        }
        return this;
    }
}

