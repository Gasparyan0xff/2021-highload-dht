package ru.mail.polis.service.gasparyansokrat;

import ru.mail.polis.lsm.Record;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

final class DataTransferChunk {

    private final Iterator<Record> data;
    private boolean isEnd;

    public static final byte[] ENDS = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    public static final byte[] NEXT_LINE = "\r\n".getBytes(StandardCharsets.UTF_8);

    private DataTransferChunk(final Iterator<Record> data) {
        this.data = data;
        this.isEnd = false;
    }

    public static DataTransferChunk build(final Iterator<Record> data) {
        return new DataTransferChunk(data);
    }

    public Iterator<Record> getData() {
        return data;
    }

    public byte[] getChunk() {
        if (isEnd) {
            return new byte[0];
        }
        ByteBuffer chunk;
        if (data.hasNext()) {
            chunk = buildChunk(data.next());
        } else {
            chunk = endChunk();
            isEnd = true;
        }
        return chunk.array();
    }

    private ByteBuffer buildChunk(final Record record) {
        final byte[] key = extractBytes(record.getKey());
        final byte[] value = extractBytes(record.getValue());
        final byte[] hexSize = Integer.toHexString(key.length + value.length + 1).getBytes(StandardCharsets.UTF_8);
        final int totalSize = hexSize.length + key.length + value.length + 5 * Byte.BYTES;
        ByteBuffer tmpChunk = ByteBuffer.allocate(totalSize);
        tmpChunk.put(hexSize);
        tmpChunk.put(NEXT_LINE);
        tmpChunk.put(key);
        tmpChunk.put("\n".getBytes(StandardCharsets.UTF_8));
        tmpChunk.put(value);
        tmpChunk.put(NEXT_LINE);
        return tmpChunk.position(0);
    }

    private byte[] extractBytes(final ByteBuffer buffer) {
        byte[] tmp = new byte[buffer.limit()];
        buffer.get(tmp);
        return tmp;
    }

    private ByteBuffer endChunk() {
        ByteBuffer tmpChunk = ByteBuffer.allocate(ENDS.length);
        tmpChunk.put(ENDS);
        return tmpChunk.position(0);
    }

}