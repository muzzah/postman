package org.postman.server;

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

import static com.siia.commons.core.check.Check.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


/**
 *
 * The core message type that is used to represent a message being sent between a client and server.
 *
 * A Postman message structure is as follows
 *
 * <p>
 * HEADER [4 bytes - Size of message] + BODY [N>0 Bytes]
 * <p>
 *
 * The body of the message is represented by {@link MessageOuterClass.Message} so that we can determine the type
 * of the class to deserialise the message to using reflection.
 *
 * The default constructor creates an instance which still has to be initalised with a buffer
 * using the read method. The read method can be called multiple times with buffers as they arrive and
 * the data will be copied in as needed until the amount of bytes as specified in the header is read in
 *
 * 2 Byte buffers used to represent the data.
 *
 * header   : 4 byte buffer that represents that size of the message. This means that the max message size is Integer.MAX_SIZE
 * body     : The body of the message whose size will be equal to that of what is in the header buffer.
 */
public class PostmanMessage {
    private static final String TAG = Logcat.getTag();
    private static final int HEADER_LENGTH = Integer.BYTES;
    private final AtomicBoolean hasFilledFrame;

    private ByteBuffer body;
    private ByteBuffer header;


    /**
     * Constructs an empty PostmanMessage that will not be fully initialised until read
     * is called and the buffers are filled as expected
     */
    public PostmanMessage() {
        hasFilledFrame = new AtomicBoolean(false);
    }

    /**
     * Constructs a postman message from a Protobuf instance. Will throw an
     * exception if the protobuf object is not initialised
     * @param msg The protobuf message to use to initialise this object.
     */
    public PostmanMessage(MessageLite msg) {
        checkState(msg.isInitialized(), "Cannot initialise postman message with invalid protobuf object");
        MessageOuterClass.Message innerFrameMsg = MessageOuterClass.Message.newBuilder()
                .setType(msg.getClass().getName())
                .setData(ByteString.copyFrom(msg.toByteArray())).build();

        body = ByteBuffer.wrap(innerFrameMsg.toByteArray());
        hasFilledFrame = new AtomicBoolean(true);
    }

    /**
     * Creates a copy of the postman message with a copy of the buffers from he msg parameter.
     * @param msg The PostmanMessage to copy
     */
    public PostmanMessage(PostmanMessage msg) {
        checkState(msg.isInitialised(), "Cannot initialise postman message with uninitialised postman messaage");
        this.body = msg.getBody();
        this.header = msg.getHeader();
        hasFilledFrame = new AtomicBoolean(true);

    }

    /**
     * Returns a bytebuffer that contains the full frame of this PostmanMessage, that is
     * the header and body. Will throw an exception of the message
     * has not been properly initialised. The buffer returned will have been flipped
     * meaning its position will be at zero
     * @return A ByteBuffer with the bytes in this message's body+header
     */
    public synchronized ByteBuffer getFrame() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        ByteBuffer frame = ByteBuffer.allocate(HEADER_LENGTH + body.limit());
        frame.putInt(body.limit());
        frame.put(body);
        frame.flip();
        body.rewind();
        return frame;
    }

    /**
     * Returns a copy of the header buffer. Throws an Exception if the postman message
     * has not been properly initialised. The buffer returned will have been flipped
     * meaning its position will be at zero
     * @return A ByteBuffer with the bytes in this message's header
     */
    public synchronized ByteBuffer getHeader() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        header.rewind();

        ByteBuffer headerCopy = ByteBuffer.allocate(HEADER_LENGTH);
        headerCopy.put(header);
        headerCopy.flip();
        header.rewind();
        return headerCopy;

    }

    /**
     * Returns a copy of the body buffer. Throws an Exception if the postman message
     * has not been properly initialised. The buffer returned will have been flipped
     * meaning its position will be at zero
     * @return A ByteBuffer with the bytes in this message's body
     */
    public synchronized ByteBuffer getBody() {
        checkState(hasFilledFrame.get(), "Frame not filled");
        body.rewind();
        ByteBuffer copy = ByteBuffer.allocate(body.limit());
        copy.put(body);
        copy.flip();
        body.rewind();
        return copy;

    }

    /**
     * Returns the protobuf object that is inside this message. Exception thrown if
     * not initialised properly.
     *
     * @param <T> The type of the protobuf class
     * @return Returns an instance of the protobuf message inside this PostmanMessage
     *
     * @throws InvalidProtocolBufferException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractMessageLite> T getProtoObj() throws InvalidProtocolBufferException,
            IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException {
        checkState(hasFilledFrame.get(), "Frame not filled");

        //TODO improve the perf here by reusing the body buffer field directly
        ByteBuffer body = getBody();

        MessageOuterClass.Message innerFrameMsg = MessageOuterClass.Message.parseFrom(body.array());
            return (T)Class.forName(innerFrameMsg.getType())
                    .getMethod("parseFrom", byte[].class)
                    .invoke(null, (Object) innerFrameMsg.getData().toByteArray());

    }

    /**
     * Checks the type of the protobuf message inside this PostmanMessage. Throws an exception
     * of the message has not been initialised fully.
     *
     * @param type The type of the protobuf type to check
     * @return True if the protobuf message matches, false otherwise
     * @throws InvalidProtocolBufferException
     */
    public boolean isOfType(Class<? extends AbstractMessageLite> type) throws InvalidProtocolBufferException {

        //TODO improve performance here by splitting out the type from the body and only reading in that
        ByteBuffer body = getBody();
        MessageOuterClass.Message innerFrameMsg = MessageOuterClass.Message.parseFrom(body.array());

        return innerFrameMsg.getType().equalsIgnoreCase(type.getName());
    }


    /**
     * Reads in the necessary bytes from the provided buffer and fills in the internal buffers
     * to match the structure as expected. First reads in the header bytes then the body bytes.
     * This method can be called multiple times but the stream of bytes must match the expected structure.
     * Once the expected number of bytes has been read (specified by the header), the message will
     * be considered full and other methods can be called to retrieve the protobuf object
     * along with internal buffers. Once full, this method should not be called again otherwise an exception
     * is thrown.
     *
     * @param buffer The buffer to read from
     * @return True if the message has been filled as expected, otherwise false and this method can be called again.
     * @throws InvalidPostmanMessageException If the structure is not as expected or the header has specified that the body is
     * 0 or less bytes.
     */
    public boolean read(ByteBuffer buffer) throws InvalidPostmanMessageException {
        checkState(!hasFilledFrame.get(), "Frame filled");

        if (!buffer.hasRemaining()) {
            Logcat.w(TAG, "Empty frame not being read");
            return false;
        }

        if (isNull(header)) {
            header = ByteBuffer.allocate(HEADER_LENGTH);
        }

        if (header.hasRemaining()) {
            IO.copyUntilDestinationFull(buffer, header);
        }

        if (isNull(body) && !header.hasRemaining()) {

            int bodyLength = header.getInt(0);

            if (bodyLength <= 0) {
                throw new InvalidPostmanMessageException(String.format("Invalid frame value %s", bodyLength));
            }

            body = ByteBuffer.allocate(bodyLength);
        }

        if (nonNull(body)) {
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

    /**
     * Checks to see if the internal buffers have been filled and this message can be used.
     * {@see #read(ByteBuffer)}
     * @return True if the message has been initialised, otherwise false
     */
    public boolean isInitialised() {
        return hasFilledFrame.get();
    }

    @Override
    public String toString() {
        String innerMessage = "";
        if(isInitialised()) {
            try {
                AbstractMessageLite protoObj = getProtoObj();
                innerMessage = protoObj.toString();
            } catch (Exception e) {
                Logcat.e(TAG, "Error in toString", e);
                innerMessage = e.getMessage();
            }
        }

        return "PostmanMessage{" +
                "bPos=" + body.position() + " " +
                "bLimit=" + body.limit() + " " +
                "bCapacity=" + body.capacity() + " " +
                "iF=" + innerMessage + " }";
    }

    public static class InvalidPostmanMessageException extends IOException {
        InvalidPostmanMessageException(String message) {
            super(message);
        }
    }

}

