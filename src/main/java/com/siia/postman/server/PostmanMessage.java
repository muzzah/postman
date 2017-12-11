package com.siia.postman.server;

import android.util.Base64;
import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import static com.siia.commons.core.check.Check.checkState;


/**
 * Postman Message Structure
 *
 * HEADER [4 bytes] + BODY [N Bytes]
 *
 * Body has to be at least one byte
 * MAX is 1 MB per message (Look into aligning this with TCP Frame size?)
 * 
 *
 */
public class PostmanMessage {
    private static final String TAG = Logcat.getTag();
    private static final long MAX_FRAME_LENGTH = 1024*1024; //1MB
    //All of the header Header TODO Convert to object
    private static final int HEADER_LENGTH = Integer.BYTES;
    private static final int MIN_BODY_LENGTH= 1;
    private final AtomicBoolean hasFilledFrame;

    private ByteBuffer body;
    private ByteBuffer header;
    private ByteBuffer buffer;

    @Inject
    public PostmanMessage() {
        body = null;
        hasFilledFrame = new AtomicBoolean(false);
    }

    public PostmanMessage(byte[] msg) {
        checkState(msg.length >= MIN_BODY_LENGTH, "Cannot initialise postman message with invalid body");
        body = ByteBuffer.wrap(msg);
        hasFilledFrame = new AtomicBoolean(true);
    }

    ByteBuffer getFrame() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        body.rewind();

        ByteBuffer frame = ByteBuffer.allocate(HEADER_LENGTH + body.limit());
        frame.putInt(body.limit());
        frame.put(body);
        frame.flip();
        return frame;

    }

    public String getMsg() {
        return Base64.encodeToString(body.array(), Base64.DEFAULT);
    }

    public ByteBuffer getBody() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        body.rewind();
        ByteBuffer copy = ByteBuffer.allocate(body.limit());
        copy.put(body);
        copy.flip();
        return copy;

    }

    @Override
    public String toString() {
        return "PostmanMessage{" +
                "bPos=" + body.position() + " " +
                "bLimit=" + body.limit()+ " " +
                "bCapacity=" + body.capacity()+ " " +
                "msg=" + getMsg()  + " }";
    }


    public boolean read(ByteBuffer buffer) throws IOException {
        checkState(!hasFilledFrame.get(), "Frame filled");


        if(!buffer.hasRemaining()) {
            Log.w(TAG, "Empty buffer not being read");
            return false;
        }

        if(header == null) {
            header = ByteBuffer.allocate(HEADER_LENGTH);
        }

        if(header.hasRemaining()) {
            IO.copyUntilDestinationFull(buffer, header);
        }

        if(body == null && !header.hasRemaining()) {

            int bodyLength = header.getInt(0);

            if (bodyLength <= 0 || bodyLength > MAX_FRAME_LENGTH - HEADER_LENGTH) {
                throw new IOException(String.format("Invalid frame value %s", bodyLength));
            }

            body = ByteBuffer.allocate(bodyLength);
        }

        if(body != null) {
            if (buffer.hasRemaining()) {
                IO.copyUntilDestinationFull(buffer, body);
            }

            if (!body.hasRemaining()) {
                hasFilledFrame.set(true);
                body.flip();
            }
        }

        return hasFilledFrame.get();


    }

    public boolean isFull() {
        return hasFilledFrame.get();
    }

    public boolean ofType(Class<?> type) {
        return false;
    }

    public ByteBuffer buffer() {
        if(buffer == null) {
            buffer = getFrame();
        }

        return buffer;
    }
}

