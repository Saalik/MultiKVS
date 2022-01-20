package com.bmartin.kvs;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

public class Transaction implements CborSerializable {
    /**
     * This needed because of the use of Map<Transaction, Record<K, V>> liveRecords.
     * TODO: is this the right way ?
     */
    public static class TransactionKeySerializer<T> extends JsonSerializer<T> {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public void serialize(T t, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, t);
            gen.writeFieldName(writer.toString());
        }
    }

    /**
     * This needed because of the use of Map<Transaction, Record<K, V>> liveRecords.
     * Transaction is used as a map key.
     * TODO: is this the right way ?
     */
    public static class TransactionKeyDeserializer extends KeyDeserializer {
        public Transaction deserializeKey(String s, DeserializationContext deserializationContext) throws IOException {
            System.out.println(">>> " + s);
//            return new JsonFactory().createParser(s).readValueAs(Transaction.class);
            return new ObjectMapper().readValue(s, Transaction.class);
//                return deserializationContext.readValue(new JsonFactory().createParser(s), Transaction.class);
        }
    }

    public int id;
    /**
     * Reference to TransactionCoordinatorActor
     */
    public ActorRef<TransactionCoordinatorActor.Command> actor;

    @JsonCreator
    public Transaction(@JsonProperty("id") int id,
                       @JsonProperty("actor") ActorRef<TransactionCoordinatorActor.Command> actor) {
        this.id = id;
        this.actor = actor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
