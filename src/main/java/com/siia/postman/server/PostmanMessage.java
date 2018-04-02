package com.siia.postman.server;

import android.util.Log;

import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import static com.siia.commons.core.check.Check.checkState;


/**
 * Postman Message Structure
 * <p>
 * HEADER [4 bytes] + BODY [N Bytes]
 * <p>
 * Body has to be at least one byte
 * MAX is 1 MB per message (Look into aligning this with TCP Frame size?)
 */
public class PostmanMessage {
    private static final String TAG = Logcat.getTag();
    private static final long MAX_FRAME_LENGTH = 1024 * 1024; //1MB
    //All of the header Header TODO Convert to object
    private static final int HEADER_LENGTH = Integer.BYTES;
    private final AtomicBoolean hasFilledFrame;

    private ByteBuffer body;
    private ByteBuffer header;
    private ByteBuffer frame;


    @Inject
    public PostmanMessage() {
        hasFilledFrame = new AtomicBoolean(false);
    }

    public PostmanMessage(MessageLite msg) {
        checkState(msg.isInitialized(), "Cannot initialise postman message with invalid proto object");
        MessageOuterClass.Message innerFrameMsg = MessageOuterClass.Message.newBuilder()
                .setType(msg.getClass().getName())
                .setData(ByteString.copyFrom(msg.toByteArray())).build();

        body = ByteBuffer.wrap(innerFrameMsg.toByteArray());
        hasFilledFrame = new AtomicBoolean(true);
    }

    public PostmanMessage(PostmanMessage msg) {
        this.body = msg.getBody();
        this.frame = msg.getFrame();
        hasFilledFrame = new AtomicBoolean(true);

    }

    //TODO these methods while private are called from other methods which can be called from different threads
    //This causes the invalid tag exception we see sometimes
    // We need a better way to prevent these
    private synchronized ByteBuffer getFrame() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        body.rewind();

        ByteBuffer frame = ByteBuffer.allocate(HEADER_LENGTH + body.limit());
        frame.putInt(body.limit());
        frame.put(body);
        frame.flip();
        body.rewind();
        return frame;

    }

    private synchronized ByteBuffer getBody() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        body.rewind();
        ByteBuffer copy = ByteBuffer.allocate(body.limit());
        copy.put(body);
        copy.flip();
        body.rewind();
        return copy;

    }
    @SuppressWarnings("unchecked")
    public <T extends AbstractMessageLite> T getProtoObj() throws InvalidProtocolBufferException,
            IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException{
        ByteBuffer body = getBody();
        byte[] data = body.array();
        MessageOuterClass.Message innerFrameMsg = MessageOuterClass.Message.parseFrom(data);
            return (T)Class.forName(innerFrameMsg.getType())
                    .getMethod("parseFrom", byte[].class)
                    .invoke(null, (Object) innerFrameMsg.getData().toByteArray());

    }

    @SuppressWarnings("unchecked")
    public boolean isOfType(Class<? extends AbstractMessageLite> type) throws InvalidProtocolBufferException,
            IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException{
        ByteBuffer body = getBody();
        byte[] bodyBuffer = body.array();
        MessageOuterClass.Message innerFrameMsg = MessageOuterClass.Message.parseFrom(bodyBuffer);

        return innerFrameMsg.getType().equalsIgnoreCase(type.getName());
    }

    @Override
    public String toString() {
        String innerMessage = "";
        if(isFull()) {
            try {
                AbstractMessageLite protoObj = getProtoObj();
                innerMessage = protoObj.getClass().getSimpleName();
            } catch (Exception e) {
                innerMessage = e.getMessage();
            }
        }

        return "PostmanMessage{" +
                "bPos=" + body.position() + " " +
                "bLimit=" + body.limit() + " " +
                "bCapacity=" + body.capacity() + " " +
                "iF=" + innerMessage + " }";
    }


    public boolean read(ByteBuffer buffer) throws IOException {
        checkState(!hasFilledFrame.get(), "Frame filled");


        if (!buffer.hasRemaining()) {
            Log.w(TAG, "Empty frame not being read");
            return false;
        }

        if (header == null) {
            header = ByteBuffer.allocate(HEADER_LENGTH);
        }

        if (header.hasRemaining()) {
            IO.copyUntilDestinationFull(buffer, header);
        }

        if (body == null && !header.hasRemaining()) {

            int bodyLength = header.getInt(0);

            if (bodyLength <= 0 || bodyLength > MAX_FRAME_LENGTH - HEADER_LENGTH) {
                throw new IOException(String.format("Invalid frame value %s", bodyLength));
            }

            body = ByteBuffer.allocate(bodyLength);
        }

        if (body != null) {
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

    public ByteBuffer frame() {
        if (frame == null) {
            frame = getFrame();
        }

        return frame;
    }

}

