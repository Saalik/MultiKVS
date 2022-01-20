package com.bmartin.kvs.journal;

import com.bmartin.kvs.CborSerializable;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jnr.ffi.annotations.In;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ObjectIndex.class, name = "objectIndex"),
        @JsonSubTypes.Type(value = TimeBasedIndex.class, name = "timeBasedIndex"),
})
public abstract class AbstractIndex<K, V> implements CborSerializable {
    /**
     * list of indexes
     */
    public enum IndexName {
        ObjectIndex, TimeBasedIndex
    }

    final Map<Integer, Map<K, JournalActor.State.Record<K, V>>> liveRecords;

    AbstractIndex(Map<Integer, Map<K, JournalActor.State.Record<K, V>>> liveRecords) {
        this.liveRecords = Objects.requireNonNullElseGet(liveRecords, HashMap::new);
    }

    AbstractIndex<K, V> begin(JournalActor.Begin event) {
        System.out.println(">> AbstractIndex::begin()");
        // TODO: return StatusReply<Done> for ack to client ?
        return this;
    }

    AbstractIndex<K, V> update(JournalActor.State.Record<K, V> record) {
        System.out.println(">> AbstractIndex::update()");
        assert (null != record.transaction);

        // instantiate a new list if needed
        Map<K, JournalActor.State.Record<K, V>> transactionRecords = liveRecords.computeIfAbsent(record.transaction.id, k -> new HashMap<>());

        // add new record to list
        transactionRecords.put(record.key, record);
        return this;
    }

    abstract AbstractIndex<K, V> lookup(JournalActor.Lookup<K, V> event);

    abstract AbstractIndex<K, V> commit(JournalActor.Commit event);

    AbstractIndex<K, V> abort(JournalActor.Abort event) {
        System.out.println(">> AbstractIndex::abort(): transactionId=" + event.transaction.id);
        liveRecords.remove(event.transaction.id);
        // reply to client
        event.replyTo.tell(new JournalActor.AbortReply(true));
        return this;
    }
}
