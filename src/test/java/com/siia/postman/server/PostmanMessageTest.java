package com.siia.postman.server;

import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.siia.postman.server.MessageOuterClass.Response;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class PostmanMessageTest {

    private Response ok = Response.newBuilder().setOk(true).build();

    @Test
    public void messageShouldReturnSameObject() throws InvalidProtocolBufferException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        PostmanMessage message = new PostmanMessage(ok);
        assertThat(message.<Response>getProtoObj()).isEqualTo(ok);

    }

    @Test
    public void messageShouldBeFilled() {
        PostmanMessage message = new PostmanMessage(ok);
        assertThat(message.isFull()).isTrue();

    }


    @Test
    public void messageShouldSpecifyCorrectType() throws InvalidProtocolBufferException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        PostmanMessage message = new PostmanMessage(ok);
        assertThat(message.isOfType(Response.class)).isTrue();

    }

    @Test
    public void messageShouldSpecifyIncorrectType() throws InvalidProtocolBufferException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        PostmanMessage message = new PostmanMessage(ok);
        assertThat(message.isOfType(AbstractMessageLite.class)).isFalse();

    }

    @Test
    public void newMessageShouldNotBeFull() {
        assertThat(new PostmanMessage().isFull()).isFalse();
    }

    @Test
    public void specifiesToProvideFurtherDataForReadingForIncompleteFrame() throws IOException {
        byte[] data = {0,0,0,3,9,9};
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(ByteBuffer.wrap(data))).isFalse();
        assertThat(message.isFull()).isFalse();

    }

    @Test
    public void specifiesFrameIsFilledIfIncompleteFrameIsFilled() throws IOException {
        byte[] data = {0,0,0,3,9,9};
        PostmanMessage message = new PostmanMessage();
        assertThat(message.read(ByteBuffer.wrap(data))).isFalse();
        assertThat(message.read(ByteBuffer.wrap(new byte[]{6}))).isTrue();
        assertThat(message.isFull()).isTrue();

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
        while(!message.isFull()) {
            message.read(ByteBuffer.wrap(new byte[]{wrapped.get()}));
        }

        ByteBuffer buffer = message.frame();
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
        assertThat(message.frame()).isSameAs(message.frame());
    }

    @Test(expected = IllegalStateException.class)
    public void cannotContinueToReadIntoFullMessageFromBuffer() throws IOException {
        PostmanMessage message = new PostmanMessage(ok);
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
    public void errorIfBodyLengthBiggerThanMax() throws IOException {
        PostmanMessage msg = new PostmanMessage();
        ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.putInt(Integer.MAX_VALUE);
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