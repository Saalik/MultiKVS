package com.bmartin.test;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import com.bmartin.kvs.Transaction;
import com.bmartin.kvs.journal.AbstractIndex;
import com.bmartin.kvs.journal.JournalActor;
import com.bmartin.kvs.journal.ObjectIndex;
import com.bmartin.kvs.journal.TimeBasedIndex;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class JournalTest {

    private static final ActorTestKit testKit = ActorTestKit.create(EventSourcedBehaviorTestKit.config().withFallback(ConfigFactory.load()));
    private final EventSourcedBehaviorTestKit<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>>
            eventSourcedTestKit = EventSourcedBehaviorTestKit.create(
            testKit.system(),
            JournalActor.create(PersistenceId.of("Journal", "1")));

    private <T> T getPrivateField(Object privateObject, Class<?> clazz, String field) {
        T ret = null;
        try {
            Field privateStringField = clazz.getDeclaredField(field);
            privateStringField.setAccessible(true);
            ret = (T) privateStringField.get(privateObject);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            fail(e);
        }
        return ret;
    }

    private <T> T getRecords(
            EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>> result,
            Class<?> indexClass,
            AbstractIndex.IndexName indexName) {
        return getPrivateField(result.state().indexes.get(indexName), indexClass, "records");
    }

    private Map<String, JournalActor.State.Record<Integer, String>> getRecordsForObjectIndex(
            EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>> result) {
        return getRecords(result, ObjectIndex.class, AbstractIndex.IndexName.ObjectIndex);
    }

    private TreeSet<JournalActor.State.Record<Integer, String>> getRecordsForTimeBasedIndex(
            EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>> result) {
        return getRecords(result, TimeBasedIndex.class, AbstractIndex.IndexName.TimeBasedIndex);
    }

    private Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> getLiveRecords(
            EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>> result,
            AbstractIndex.IndexName indexName) {
        return getPrivateField(result.state().indexes.get(indexName), AbstractIndex.class, "liveRecords");
    }

    private Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> getLiveRecordsForObjectIndex(
            EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>> result) {
        return getLiveRecords(result, AbstractIndex.IndexName.ObjectIndex);
    }

    private Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> getLiveRecordsForTimeBasedIndex(
            EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>> result) {
        return getLiveRecords(result, AbstractIndex.IndexName.TimeBasedIndex);
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    @BeforeEach
    public void setUp() {
        eventSourcedTestKit.clear();
        final int numberOfShards = ConfigFactory.load().getInt("akka.cluster.sharding.number-of-shards");
        assertTrue(numberOfShards > 0);
    }

    @AfterEach
    public void tearDown() {
        deleteDirectory(new File("snapshots"));
    }


    @Test
    public void beginTransaction() {
        EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>>
                result = eventSourcedTestKit.runCommand(new JournalActor.Begin(System.currentTimeMillis(), new Transaction(1, null)));

        Map<String, JournalActor.State.Record<Integer, String>> records = getRecordsForObjectIndex(result);

        assertEquals(0, records.size());
        assertFalse(result.hasNoEvents());
    }

    @Test
    public void updateTransaction() {
        final Transaction transaction = new Transaction(1, null);
        final long timeStamp = 42;
        final String key = "0"; // TODO: should work with int type
        final String value = "hello";

        EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>>
                result = eventSourcedTestKit.runCommand(new JournalActor.Update<>(timeStamp, transaction, key, value));

        // TODO: should be Map<Integer, Map<Integer, JournalActor.State.Record<Integer, String>>> liveRecords
        //  see https://github.com/akka/akka/issues/28566
        Map<String, JournalActor.State.Record<Integer, String>> records = getRecordsForObjectIndex(result);
        Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> liveRecords = getLiveRecordsForObjectIndex(result);

        assertEquals(0, records.size());
        assertEquals(1, liveRecords.size());
        assertEquals(1, liveRecords.get(transaction.id).size());

        JournalActor.State.Record<Integer, String> record = liveRecords.get(transaction.id).get(key);
        assertNotNull(record);
        assertEquals(transaction, record.transaction);
        assertEquals(key, record.key);
        assertEquals(value, record.value);
        assertEquals(JournalActor.State.Record.Type.LIVE, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(0, record.commitTime);
    }

    @Test
    public void commitTransaction() {
        final Transaction transaction = new Transaction(1, null);
        final long timeStamp = 42;
        final long commitTimeStamp = 51;
        final String key1 = "0";
        final String value1 = "hello";
        final String key2 = "1";
        final String value2 = "hi !";

        // update 1
        EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>>
                result = eventSourcedTestKit.runCommand(new JournalActor.Update<>(timeStamp, transaction, key1, value1));

        Map<String, JournalActor.State.Record<Integer, String>> records = getRecordsForObjectIndex(result);
        Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> liveRecords = getLiveRecordsForObjectIndex(result);

        assertEquals(0, records.size());
        assertEquals(1, liveRecords.size());
        assertEquals(1, liveRecords.get(transaction.id).size());

        JournalActor.State.Record<Integer, String> record = liveRecords.get(transaction.id).get(key1);
        assertNotNull(record);
        assertEquals(transaction, record.transaction);
        assertEquals(key1, record.key);
        assertEquals(value1, record.value);
        assertEquals(JournalActor.State.Record.Type.LIVE, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(0, record.commitTime);

        // update 2: different key and value
        result = eventSourcedTestKit.runCommand(new JournalActor.Update<>(timeStamp, transaction, key2, value2));

        records = getRecordsForObjectIndex(result);
        liveRecords = getLiveRecordsForObjectIndex(result);

        assertEquals(0, records.size());
        assertEquals(1, liveRecords.size());
        assertEquals(2, liveRecords.get(transaction.id).size());

        record = liveRecords.get(transaction.id).get(key2);
        assertNotNull(record);
        assertEquals(transaction, record.transaction);
        assertEquals(key2, record.key);
        assertEquals(value2, record.value);
        assertEquals(JournalActor.State.Record.Type.LIVE, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(0, record.commitTime);

        // update 3: same key, different value
        result = eventSourcedTestKit.runCommand(new JournalActor.Update<>(timeStamp, transaction, key1, value2));

        records = getRecordsForObjectIndex(result);
        liveRecords = getLiveRecordsForObjectIndex(result);

        assertEquals(0, records.size());
        assertEquals(1, liveRecords.size());
        assertEquals(2, liveRecords.get(transaction.id).size());

        record = liveRecords.get(transaction.id).get(key1);
        assertNotNull(record);
        assertEquals(transaction, record.transaction);
        assertEquals(key1, record.key);
        assertEquals(value2, record.value);
        assertEquals(JournalActor.State.Record.Type.LIVE, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(0, record.commitTime);

        // commit
        EventSourcedBehaviorTestKit.CommandResultWithReply<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>, JournalActor.CommitReply>
                commitResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Commit(replyTo, commitTimeStamp, transaction));

        records = getRecordsForObjectIndex(commitResult);
        liveRecords = getLiveRecordsForObjectIndex(commitResult);

        assertEquals(2, records.size());
        assertEquals(0, liveRecords.size());

        // check key1
        record = records.get(key1);
        assertFalse(liveRecords.containsKey(transaction.id));
        assertEquals(transaction, record.transaction);
        assertEquals(key1, record.key);
        assertEquals(value2, record.value);
        assertEquals(JournalActor.State.Record.Type.COMMITTED, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(commitTimeStamp, record.commitTime);

        // check key2
        record = records.get(key2);
        assertFalse(liveRecords.containsKey(transaction.id));
        assertEquals(transaction, record.transaction);
        assertEquals(key2, record.key);
        assertEquals(value2, record.value);
        assertEquals(JournalActor.State.Record.Type.COMMITTED, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(commitTimeStamp, record.commitTime);
    }

    @Test
    public void abortTransaction() {
        final Transaction transaction = new Transaction(1, null);
        final long timeStamp = 42;
        final String key = "0";
        final String value = "hello";

        // update
        EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>>
                result = eventSourcedTestKit.runCommand(new JournalActor.Update<>(timeStamp, transaction, key, value));

        Map<String, JournalActor.State.Record<Integer, String>> records = getRecordsForObjectIndex(result);
        Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> liveRecords = getLiveRecordsForObjectIndex(result);

        assertEquals(0, records.size());
        assertEquals(1, liveRecords.size());
        assertEquals(1, liveRecords.get(transaction.id).size());

        JournalActor.State.Record<Integer, String> record = liveRecords.get(transaction.id).get(key);
        assertNotNull(record);
        assertEquals(transaction, record.transaction);
        assertEquals(key, record.key);
        assertEquals(value, record.value);
        assertEquals(JournalActor.State.Record.Type.LIVE, record.type);
        assertEquals(timeStamp, record.timeStamp);
        assertEquals(0, record.commitTime);

        // abort
        EventSourcedBehaviorTestKit.CommandResultWithReply<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>, JournalActor.AbortReply>
                commitResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Abort(replyTo, System.currentTimeMillis(), transaction));

        records = getRecordsForObjectIndex(commitResult);
        liveRecords = getLiveRecordsForObjectIndex(commitResult);

        assertEquals(0, records.size());
        assertEquals(0, liveRecords.size());
    }

    void checkTimeBasedLookupReply(int lowBound, final Transaction transaction, final String value, long commitTimeStamp,
                                   EventSourcedBehaviorTestKit.CommandResultWithReply<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>, JournalActor.LookupReply<Integer, String>>
                                           lookupResult) {
        for (JournalActor.State.Record<Integer, String> record : lookupResult.reply().value) {
            assertEquals(10 - lowBound, record.key);
            assertEquals(value + '-' + (10 - lowBound), record.value);
            assertEquals(transaction, record.transaction);
            assertEquals(lowBound, record.timeStamp);
            assertEquals(commitTimeStamp, record.commitTime);
            assertEquals(JournalActor.State.Record.Type.COMMITTED, record.type);
            ++lowBound;
        }
    }

    @Test
    public void timeBaseLookup() {
        final Transaction transaction = new Transaction(1, null);
        final String value = "hello";
        final long commitTimeStamp = 51;

        EventSourcedBehaviorTestKit.CommandResult<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>>
                result = null;

        // updates
        for (int i = 0; i < 10; ++i) {
            result = eventSourcedTestKit.runCommand(new JournalActor.Update<>(10 - i, transaction, i, value + '-' + i));
        }

        Map<Integer, Map<String, JournalActor.State.Record<Integer, String>>> liveRecords = getLiveRecordsForTimeBasedIndex(result);
        TreeSet<JournalActor.State.Record<Integer, String>> records = getRecordsForTimeBasedIndex(result);


        assertEquals(0, records.size());
        assertEquals(1, liveRecords.size());
        assertEquals(10, liveRecords.get(transaction.id).size());

        // commit
        EventSourcedBehaviorTestKit.CommandResultWithReply<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>, JournalActor.CommitReply>
                commitResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Commit(replyTo, commitTimeStamp, transaction));

        liveRecords = getLiveRecordsForTimeBasedIndex(commitResult);
        records = getRecordsForTimeBasedIndex(commitResult);

        assertEquals(10, records.size());
        assertEquals(0, liveRecords.size());

        // lookup lowbound 10
        EventSourcedBehaviorTestKit.CommandResultWithReply<JournalActor.Command, JournalActor.Event, JournalActor.State<Integer, String>, JournalActor.LookupReply<Integer, String>>
                lookupResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Lookup<>(replyTo, transaction, null, 0, JournalActor.Lookup.UNUSED));
        assertEquals(10, lookupResult.reply().value.size());
        checkTimeBasedLookupReply(1, transaction, value, commitTimeStamp, lookupResult);

        // lookup lowbound 5
        lookupResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Lookup<>(replyTo, transaction, null, 5, JournalActor.Lookup.UNUSED));
        assertEquals(6, lookupResult.reply().value.size());
        checkTimeBasedLookupReply(5, transaction, value, commitTimeStamp, lookupResult);

        // lookup highbound 10
        lookupResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Lookup<>(replyTo, transaction, null, JournalActor.Lookup.UNUSED, 11));
        assertEquals(10, lookupResult.reply().value.size());
        checkTimeBasedLookupReply(1, transaction, value, commitTimeStamp, lookupResult);

        // lookup highbound 5
        lookupResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Lookup<>(replyTo, transaction, null, JournalActor.Lookup.UNUSED, 5));
        assertEquals(4, lookupResult.reply().value.size());
        checkTimeBasedLookupReply(1, transaction, value, commitTimeStamp, lookupResult);

        // lookup lowbound + highbound
        lookupResult = eventSourcedTestKit.runCommand(replyTo -> new JournalActor.Lookup<>(replyTo, transaction, null, 1, 6));
        assertEquals(5, lookupResult.reply().value.size());
        checkTimeBasedLookupReply(1, transaction, value, commitTimeStamp, lookupResult);


        // check records address if multiple indexes. They should be the same: no copies
        assertEquals(2, result.state().indexes.size());
        assertTrue(result.state().indexes.containsKey(AbstractIndex.IndexName.ObjectIndex));
        assertTrue(result.state().indexes.containsKey(AbstractIndex.IndexName.TimeBasedIndex));

        Map<Integer, JournalActor.State.Record<Integer, String>> objectRecords =
                getRecords(result, ObjectIndex.class, AbstractIndex.IndexName.ObjectIndex);

        TreeSet<JournalActor.State.Record<Integer, String>> timeRecords =
                getRecords(result, TimeBasedIndex.class, AbstractIndex.IndexName.TimeBasedIndex);

        assertEquals(objectRecords.size(), timeRecords.size());

        List<JournalActor.State.Record<Integer, String>> sortedObjectRecordsList = new LinkedList<>(objectRecords.values());
        sortedObjectRecordsList.sort(Comparator.comparingLong(o -> o.timeStamp));

        List<JournalActor.State.Record<Integer, String>> sortedTimeRecordsList = new LinkedList<>(timeRecords);
        sortedTimeRecordsList.sort(Comparator.comparingLong(o -> o.timeStamp));

        assertEquals(sortedObjectRecordsList.size(), sortedTimeRecordsList.size());

        Iterator<JournalActor.State.Record<Integer, String>> it1 = sortedObjectRecordsList.iterator();
        Iterator<JournalActor.State.Record<Integer, String>> it2 = sortedTimeRecordsList.iterator();

        while (it1.hasNext() && it2.hasNext()) {
            assertSame(it1.next(), it2.next());
        }
    }

}
