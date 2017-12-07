package com.siia.postman.server;

import android.util.Base64;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import static com.siia.commons.core.check.Check.checkState;

public class PostmanMessage {
    private static final long MAX_MESSAGE_LENGTH = 1024*1024; //1MB
    private static final int HEADER_LENGTH = Integer.BYTES;
    private static final int MIN_FRAME_LENGTH = HEADER_LENGTH+1;
    private int bodyLength;
    private ByteBuffer frame;
    private ByteBuffer body;
    private boolean filled;

    public PostmanMessage() {
        bodyLength = 0;
        frame = null;
        body = null;
        filled = false;
    }

    public PostmanMessage(byte[] msg) {
        checkState(msg.length > 0, "Cannot initialise postman message with invalid frame length");
        body = ByteBuffer.wrap(msg);
        bodyLength = msg.length;
        createFrame();
    }

    private void  createFrame() {
        body.rewind();
        if(body.capacity() != bodyLength) {
            throw new IllegalStateException("Frame length differs from body length");
        }
        frame = ByteBuffer.allocate(HEADER_LENGTH + bodyLength);
        frame.putInt(bodyLength);
        frame.put(body);
        frame.flip();
        filled = true;

    }

    public String getMsg() {
        return Base64.encodeToString(body.array(), Base64.DEFAULT);
    }

    public ByteBuffer getFrame() {
        return frame;
    }

    public ByteBuffer getBody() {
        checkState(bodyLength == body.limit(), "Frame length different from buffer capacity");
        ByteBuffer copy = ByteBuffer.allocate(bodyLength);
        body.rewind();
        copy.put(body);
        copy.flip();
        return copy;

    }

    @Override
    public String toString() {
        return "PostmanMessage{" +
                "bodyLength=" + bodyLength + " " +
                "pos=" + frame.position() + " " +
                "limit=" + frame.limit()+ " " +
                "capacity=" + frame.capacity()+ " " +
                "msg=" + getMsg()  + " }";
    }


    public boolean read(ByteBuffer buffer) throws IOException {
        if(filled) {
            throw new IllegalStateException("Message already filled");
        }

        if(bodyLength == 0) {

            if(buffer.remaining() < MIN_FRAME_LENGTH) {
                throw new IOException("Not enough bytes for frame");
            }

            bodyLength = buffer.getInt(0);

            if(bodyLength <= 0 || bodyLength > MAX_MESSAGE_LENGTH) {
                throw new IOException(String.format("Invalid frame value %s", bodyLength));
            }

            body = ByteBuffer.allocate(bodyLength);
            buffer.position(HEADER_LENGTH);

        }


        try {
            body.put(buffer);
        }catch (BufferOverflowException error) {
            throw new IOException("More frame data provided than frame length");
        }


        if(body.position() == body.capacity()) {
            createFrame();
            return true;
        }

        return false;
    }

    public boolean isFilled() {
        return filled;
    }
}

