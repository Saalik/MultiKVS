package com.bmartin.kvs.journal;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.bmartin.kvs.CborSerializable;
import com.bmartin.kvs.Transaction;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collection;
import java.util.EnumMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Persisted Actor using Event Sourcing (https://doc.akka.io/docs/akka/current/typed/persistence.html)
 * JournalActor should be sharded with 1 shard per DC
 *
 * @param <K>
 * @param <V>
 */
public class JournalActor<K, V> extends EventSourcedBehavior<JournalActor.Command, JournalActor.Event, JournalActor.State<K, V>> {
    public static final EntityTypeKey<Command> SHARD_KEY = EntityTypeKey.create(Command.class, "JournalActor");
    final ActorContext<JournalActor.Command> context;

    /**
     * COMMAND
     * not persisted: read only, does not change state
     */
    public interface Command extends CborSerializable {
    }

    public static class LookupReply<K, V> implements Command {
        public SortedSet<State.Record<K, V>> value = new TreeSet<>();

        @JsonCreator
        public LookupReply(SortedSet<State.Record<K, V>> value) {
            this.value = value;
        }

        public LookupReply(State.Record<K, V> record) {
            if (null != record) {
                value.add(record);
            }
        }

        public LookupReply(Collection<State.Record<K, V>> records) {
            assert (null != records);
            value.addAll(records);
        }
    }

    public static class CommitReply implements Command {
        public final boolean value;

        @JsonCreator
        public CommitReply(boolean value) {
            this.value = value;
        }
    }

    public static class AbortReply implements Command {
        public final boolean value;

        @JsonCreator
        public AbortReply(boolean value) {
            this.value = value;
        }
    }

    /**
     * EVENT
     * persisted: changes state
     */
    public interface Event {
        enum Type {SYSTEM, TRANSACTION}
    }

    public static class Lookup<K, V> implements Command, Event {
        public static final long UNUSED = -1L;

        public final ActorRef<LookupReply<K, V>> replyTo;
        public final Transaction transaction;
        public final K key;
        public final long lowBound;
        public final long highBound;

        public Lookup(ActorRef<LookupReply<K, V>> replyTo, Transaction transaction, K key) {
            this.replyTo = replyTo;
            this.transaction = transaction;
            this.key = key;
            this.lowBound = UNUSED;
            this.highBound = UNUSED;
        }

        @JsonCreator
        public Lookup(ActorRef<LookupReply<K, V>> replyTo, Transaction transaction, K key, long lowBound, long highBound) {
            this.replyTo = replyTo;
            this.transaction = transaction;
            this.key = key;
            this.lowBound = lowBound;
            this.highBound = highBound;
        }

        public Lookup(final Lookup<K, V> other) {
            this.replyTo = other.replyTo;
            this.transaction = other.transaction;
            this.key = other.key;
            this.lowBound = other.lowBound;
            this.highBound = other.highBound;
        }
    }

    public static class Begin implements Command, Event {
        public final long timeStamp;
        public final Transaction transaction;
        public final Type type;

        public Begin(long timeStamp, Transaction transaction) {
            this.timeStamp = timeStamp;
            this.transaction = transaction;
            this.type = Type.TRANSACTION;
        }
    }

    public static class Update<K, V> implements Command, Event {
        public final long timeStamp;
        public final Transaction transaction;
        public final Type type;
        public final K key;
        public final V value;

        public Update(long timeStamp, Transaction transaction, K key, V value) {
            this.timeStamp = timeStamp;
            this.transaction = transaction;
            this.type = Type.TRANSACTION;
            this.key = key;
            this.value = value;
        }
    }

    public static class Commit implements Command, Event {
        public final ActorRef<CommitReply> replyTo;
        public final long commitTimeStamp;
        public final Transaction transaction;
        public final Type type;

        public Commit(ActorRef<CommitReply> replyTo, long commitTimeStamp, Transaction transaction) {
            this.replyTo = replyTo;
            this.commitTimeStamp = commitTimeStamp;
            this.transaction = transaction;
            this.type = Type.TRANSACTION;
        }

        public Commit(final Commit other) {
            this.replyTo = other.replyTo;
            this.commitTimeStamp = other.commitTimeStamp;
            this.transaction = other.transaction;
            this.type = other.type;
        }
    }

    public static class Abort implements Command, Event {
        public final ActorRef<AbortReply> replyTo;
        public final long timeStamp;
        public final Transaction transaction;
        public final Type type;
        // TODO: list of participants ?

        public Abort(ActorRef<AbortReply> replyTo, long timeStamp, Transaction transaction) {
            this.replyTo = replyTo;
            this.timeStamp = timeStamp;
            this.transaction = transaction;
            this.type = Type.TRANSACTION;
        }

        public Abort(final Abort other) {
            this.replyTo = other.replyTo;
            this.timeStamp = other.timeStamp;
            this.transaction = other.transaction;
            this.type = other.type;
        }
    }

    /**
     * STATE
     */
    public static class State<K, V> implements CborSerializable {

        /**
         * Log record
         * TODO: add missing fields
         * Note: must be static for Jackson serialization/deserialization
         */
        public static class Record<K, V> implements CborSerializable, Comparable<Record<K, V>> {
            public enum Type {LIVE, ABORTED, COMMITTED}

            public final Transaction transaction;
            public final K key;
            public final V value;
            public Type type;
            public long timeStamp;
            public long commitTime;

            @JsonCreator
            Record(Transaction transaction, K key, V value, Type type, long timeStamp, long commitTime) {
                this.transaction = transaction;
                this.key = key;
                this.value = value;
                this.type = type;
                this.timeStamp = timeStamp;
                this.commitTime = commitTime;
            }

            Record(long timeStamp) {
                this.transaction = null;
                this.key = null;
                this.value = null;
                this.type = null;
                this.timeStamp = timeStamp;
                this.commitTime = -1;
            }

            @Override
            public int compareTo(Record<K, V> o) {
                return Long.compare(this.timeStamp, o.timeStamp);
            }
        }


        public EnumMap<AbstractIndex.IndexName, AbstractIndex<K, V>> indexes;

        @JsonCreator
        private State(EnumMap<AbstractIndex.IndexName, AbstractIndex<K, V>> indexes) {
            if (null == indexes) {
                this.indexes = new EnumMap<>(AbstractIndex.IndexName.class);
                this.indexes.put(AbstractIndex.IndexName.ObjectIndex, new ObjectIndex<>(null, null));
                this.indexes.put(AbstractIndex.IndexName.TimeBasedIndex, new TimeBasedIndex<>(null, null));
            } else {
                assert (indexes.size() > 0);
                this.indexes = indexes;
            }
        }

        public State<K, V> begin(Begin event) {
            System.out.println(">> State::begin()");
            indexes.values().forEach(i -> i = i.begin(event));
            // TODO: return StatusReply<Done> for ack to client ?
            return this;
        }

        public State<K, V> update(Object event) {
            System.out.println(">> State::update()");
            assert (event instanceof Update);
            Update<K, V> updateEvent = (Update<K, V>) event;

            assert (null != updateEvent.transaction);

            Record<K, V> record = new Record<>(updateEvent.transaction,
                    updateEvent.key,
                    updateEvent.value,
                    JournalActor.State.Record.Type.LIVE,
                    updateEvent.timeStamp, 0);

            indexes.values().forEach(i -> i = i.update(record));

            System.out.println(">>> " + indexes.get(AbstractIndex.IndexName.ObjectIndex).liveRecords.values() + " : " + this);

            return this;
        }

        public State<K, V> lookup(Lookup<K, V> event) {
            System.out.println(">> State::lookup()");

            if (JournalActor.Lookup.UNUSED != event.lowBound || JournalActor.Lookup.UNUSED != event.highBound) {
                indexes.get(AbstractIndex.IndexName.TimeBasedIndex).lookup(event);
            } else {
                indexes.get(AbstractIndex.IndexName.ObjectIndex).lookup(event);
            }
            return this;
        }

        public State<K, V> commit(Commit event) {
            System.out.println(">> State::commit(): transactionId=" + event.transaction);
            indexes.values().forEach(i -> i = i.commit(event));
            return this;
        }

        public State<K, V> abort(Abort event) {
            System.out.println(">> State::abort(): transactionId=" + event.transaction);
            indexes.values().forEach(i -> i = i.abort(event));
            return this;
        }
    }


    public static Behavior<Command> create(PersistenceId persistenceId) {
        return Behaviors.setup(ctx -> new JournalActor<>(persistenceId, ctx));
    }

    private JournalActor(PersistenceId persistenceId, ActorContext<JournalActor.Command> context) {
        super(persistenceId);
        this.context = context;
        context.getLog().debug("JournalActor::JournalActor() : persistenceId=" + persistenceId + " : " + this);
    }

    @Override
    public State<K, V> emptyState() {
        return new State<>(null);
    }

    @Override
    public CommandHandler<Command, Event, State<K, V>> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Begin.class, command -> Effect().persist(command))
                .onCommand(Update.class, command -> Effect().persist(command))
                .onCommand(Commit.class, command -> Effect().persist(command))
                .onCommand(Abort.class, command -> Effect().persist(command))
                .onCommand(Lookup.class, command -> Effect().persist(command))  // TODO: check this, Effect().none() ?
                .build();
    }

    @Override
    public EventHandler<State<K, V>, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(Begin.class, State::begin)
                .onEvent(Update.class, State::update)
                .onEvent(Commit.class, State::commit)
                .onEvent(Abort.class, State::abort)
                .onEvent(Lookup.class, State::lookup)
                .build();
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(100, 2).withDeleteEventsOnSnapshot();
    }

    @Override
    public boolean shouldSnapshot(State<K, V> state, Event event, long sequenceNr) {
        return event instanceof Commit;
    }
}