package org.postman.server;

import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;
import org.postman.server.nio.Test.Ping;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class PostmanMessageTest {

    private Ping ping = Ping.getDefaultInstance();

    @Test
    public void messageShouldReturnSameObject() throws InvalidProtocolBufferException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        PostmanMessage message = new PostmanMessage(ping);
        assertThat(message.<Ping>getProtoObj()).isEqualTo(ping);

    }

    @Test
    public void messageShouldBeFilled() {
        PostmanMessage message = new PostmanMessage(ping);
        assertThat(message.isInitialised()).isTrue();

    }


    @Test
    public void messageShouldSpecifyCorrectType() throws InvalidProtocolBufferException {
        PostmanMessage message = new PostmanMessage(ping);
        assertThat(message.isOfType(Ping.class)).isTrue();

    }

    @Test
    public void messageShouldSpecifyIncorrectType() throws InvalidProtocolBufferException {
        PostmanMessage message = new PostmanMessage(ping);
        assertThat(message.isOfType(AbstractMessageLite.class)).isFalse();

    }

    @Test
    public void newMessageShouldNotBeFull() {
        assertThat(new PostmanMessage().isInitialised()).isFalse();
    }

    @Test
    public void specifiesToProvideFurtherDataForReadingForIncompleteFrame() throws IOException {
        byte[] data = {0,0,0,3,9,9};
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(ByteBuffer.wrap(data))).isFalse();
        assertThat(message.isInitialised()).isFalse();

    }

    @Test
    public void specifiesFrameIsFilledIfIncompleteFrameIsFilled() throws IOException {
        byte[] data = {0,0,0,3,9,9};
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(ByteBuffer.wrap(data))).isFalse();
        assertThat(message.read(ByteBuffer.wrap(new byte[]{6}))).isTrue();
        assertThat(message.isInitialised()).isTrue();

    }


    @Test(expected = IllegalStateException.class)
    public void cannotContinueToReadIntoFullMessage() throws IOException {
        byte[] data = {0,0,0,3,9,9,1};
        ByteBuffer wrapped = ByteBuffer.wrap(data);
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(wrapped)).isTrue();
        message.read(ByteBuffer.allocate(1));
    }

    @Test
    public void canReadLessThanInBufferToFillMessage() throws IOException {
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(ByteBuffer.wrap(new byte[]{0,0,0}))).isFalse();
        assertThat(message.read(ByteBuffer.wrap(new byte[]{5,1}))).isFalse();
        ByteBuffer lastBuffer = ByteBuffer.wrap(new byte[]{2, 3, 4, 5, 0, 0, 0});
        assertThat(message.read(lastBuffer)).isTrue();
        assertThat(lastBuffer.position()).isEqualTo(4);
    }

    @Test
    public void readOneByOne() throws IOException {
        byte[] data = {0,0,0,3,9,9,1};
        ByteBuffer wrapped = ByteBuffer.wrap(data);
        PostmanMessage message = new PostmanMessage();
        while(!message.isInitialised()) {
            message.read(ByteBuffer.wrap(new byte[]{wrapped.get()}));
        }

        ByteBuffer buffer = message.getFrame();
        assertThat(buffer.limit()).isEqualTo(data.length);
        assertThat(buffer.capacity()).isEqualTo(data.length);
        assertThat(buffer.position()).isZero();
        assertThat(buffer.array()).isEqualTo(data);

    }

    @Test
    public void frameIsSameOverMultipleInvocations() throws IOException {
        byte[] data = {0,0,0,3,9,9,1};
        PostmanMessage message = new PostmanMessage();
        message.read(ByteBuffer.wrap(data));
        assertThat(message.getFrame()).isEqualTo(message.getFrame());
    }

    @Test(expected = IllegalStateException.class)
    public void cannotContinueToReadIntoFullMessageFromBuffer() throws IOException {
        PostmanMessage message = new PostmanMessage(ping);
        message.read(ByteBuffer.allocate(1));
    }

    @Test
    public void specifiesFrameIsFilled() throws IOException {
        byte[] data = {0,0,0,3,9,9,0};
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(ByteBuffer.wrap(data))).isTrue();

    }

    @Test(expected = IOException.class)
    public void errorIfBodyLengthZero() throws IOException {
        PostmanMessage msg = new PostmanMessage();
        ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.putInt(0);
        buffer.rewind();
        msg.read(buffer);
    }

    @Test(expected = IOException.class)
    public void errorIfBodyLengthNegaitive() throws IOException {
        PostmanMessage msg = new PostmanMessage();
        ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.putInt(-1);
        buffer.rewind();
        msg.read(buffer);
    }


}