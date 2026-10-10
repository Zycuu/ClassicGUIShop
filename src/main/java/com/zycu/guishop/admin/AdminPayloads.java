package com.zycu.guishop.admin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/** Bounded chunks also support established shops larger than one custom payload. */
public final class AdminPayloads {
    public static final int CHUNK_SIZE = 6000;
    public static final int MAX_CHUNKS = 512;
    public static final int MAX_DOCUMENT = 2_000_000;
    private AdminPayloads() {}

    public record Message(String id, String action, String revision, int index, int count, String body) {}

    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath("guishop", "admin_open_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = new StreamCodec<>() {
            public Open decode(RegistryFriendlyByteBuf buffer) { return new Open(); }
            public void encode(RegistryFriendlyByteBuf buffer, Open payload) {}
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static Message read(RegistryFriendlyByteBuf buffer) {
        return new Message(buffer.readUtf(36), buffer.readUtf(24), buffer.readUtf(128),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(CHUNK_SIZE));
    }
    private static void write(RegistryFriendlyByteBuf buffer, Message message) {
        buffer.writeUtf(message.id(), 36); buffer.writeUtf(message.action(), 24);
        buffer.writeUtf(message.revision(), 128); buffer.writeVarInt(message.index());
        buffer.writeVarInt(message.count()); buffer.writeUtf(message.body(), CHUNK_SIZE);
    }
    public record Request(Message message) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("guishop", "admin_request_v6"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            public Request decode(RegistryFriendlyByteBuf buffer) { return new Request(read(buffer)); }
            public void encode(RegistryFriendlyByteBuf buffer, Request payload) { write(buffer, payload.message()); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Reply(Message message) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("guishop", "admin_reply_v6"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = new StreamCodec<>() {
            public Reply decode(RegistryFriendlyByteBuf buffer) { return new Reply(read(buffer)); }
            public void encode(RegistryFriendlyByteBuf buffer, Reply payload) { write(buffer, payload.message()); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static List<Message> chunks(String id, String action, String revision, String body) {
        if (body.length() > MAX_DOCUMENT) throw new IllegalArgumentException("Editor response exceeds the Alpha size limit");
        List<String> pieces = new ArrayList<>();
        for (int offset = 0; offset < body.length();) {
            int end = Math.min(body.length(), offset + CHUNK_SIZE);
            // Never cut a UTF-16 surrogate pair between packets.
            if (end < body.length() && Character.isHighSurrogate(body.charAt(end - 1))) end--;
            pieces.add(body.substring(offset, end)); offset = end;
        }
        if (pieces.isEmpty()) pieces.add("");
        List<Message> result = new ArrayList<>();
        for (int index = 0; index < pieces.size(); index++) {
            result.add(new Message(id, action, revision, index, pieces.size(), pieces.get(index)));
        }
        return result;
    }

    public static final class Assembly {
        private final String id, action, revision;
        private final int count;
        private int next;
        private final StringBuilder body = new StringBuilder();
        private final long started = System.nanoTime();
        public Assembly(Message first) {
            if (first.count() < 1 || first.count() > MAX_CHUNKS || first.index() != 0) {
                throw new IllegalArgumentException("Invalid editor packet sequence");
            }
            id = first.id(); action = first.action(); revision = first.revision(); count = first.count();
        }
        public boolean expired() { return System.nanoTime() - started > 30_000_000_000L; }
        public boolean add(Message part) {
            if (expired() || !id.equals(part.id()) || !action.equals(part.action())
                || !revision.equals(part.revision()) || count != part.count() || part.index() != next
                || part.body().length() > CHUNK_SIZE || body.length() + part.body().length() > MAX_DOCUMENT) {
                throw new IllegalArgumentException("Invalid or expired editor packet sequence");
            }
            body.append(part.body()); next++; return next == count;
        }
        public String body() { return body.toString(); }
    }
}
