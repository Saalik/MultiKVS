package com.bmartin.kvs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class TransactionJSONSerializer extends StdSerializer<Transaction> {

    public TransactionJSONSerializer() {
        super(Transaction.class);
    }

    public TransactionJSONSerializer(Class<Transaction> t) {
        super(t);
    }

    @Override
    public void serialize(Transaction transaction, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("id", transaction.id);
        gen.writeObjectField("actor", transaction.actor.path());
        gen.writeEndObject();
    }

//    @Override
//    public Transaction deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
//        return null;
//    }


//    @Override
//    public void serialize(
//            Item value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
//
//        jgen.writeStartObject();
//        jgen.writeNumberField("id", value.id);
//        jgen.writeStringField("itemName", value.itemName);
//        jgen.writeNumberField("owner", value.owner.id);
//        jgen.writeEndObject();
//    }
}