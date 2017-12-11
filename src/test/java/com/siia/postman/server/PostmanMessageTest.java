package com.siia.postman.server;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class PostmanMessageTest {

    private byte[] body = {0,1,2,3,4,5};
    private byte[] validFrame = {0,0,0,3,6,7,8};


    @Test
    public void frameShouldBeSetFromArray() {
        PostmanMessage message = validFromBody();
        assertThat(message.getFrame().limit()).isEqualTo(body.length + Integer.BYTES);
        assertThat(message.getFrame().capacity()).isEqualTo(body.length + Integer.BYTES);
        assertThat(message.getFrame().position()).isZero();
    }

    @Test
    public void bodyShouldBeSetFromArray() {
        PostmanMessage message = validFromBody();
        assertThat(message.getBody().limit()).isEqualTo(body.length);
        assertThat(message.getBody().capacity()).isEqualTo(body.length);
        assertThat(message.getBody().position()).isZero();
    }

    @Test
    public void bodyShouldBeCopy() {
        PostmanMessage message = validFromBody();
        assertThat(message.getBody()).isNotSameAs(message.getBody());
    }

    @Test
    public void frameShouldBeCopy() {
        PostmanMessage message = validFromBody();
        assertThat(message.getFrame()).isNotSameAs(message.getFrame());
    }

    @Test
    public void frameContentShouldBeSet() {
        PostmanMessage message = validFromBody();
        assertThat(message.getFrame().array()).isEqualTo(new byte[]{0,0,0,6,0,1,2,3,4,5});
    }

    @Test
    public void bodyContentShouldBeSetFromArray() {
        PostmanMessage message = validFromBody();
        assertThat(message.getBody().array()).isEqualTo(body);
    }


    @Test
    public void isFullShouldBeTrue() {
        PostmanMessage message = validFromBody();
        assertThat(message.isFull()).isTrue();
    }

    @Test(expected = IllegalStateException.class)
    public void exceptionForBodyWithZeroBytes() {
        new PostmanMessage(new byte[]{});
    }

    @Test
    public void shouldAcceptOneByteBody() {
        PostmanMessage smallMessage = new PostmanMessage(new byte[]{0});

        assertThat(smallMessage.getFrame().limit()).isEqualTo(1 + Integer.BYTES);
        assertThat(smallMessage.getFrame().capacity()).isEqualTo(1 + Integer.BYTES);
        assertThat(smallMessage.getFrame().position()).isZero();
        assertThat(smallMessage.getFrame().array()).isEqualTo(new byte[]{0,0,0,1,0});

        assertThat(smallMessage.getBody().limit()).isEqualTo(1);
        assertThat(smallMessage.getBody().capacity()).isEqualTo(1);
        assertThat(smallMessage.getBody().position()).isZero();
        assertThat(smallMessage.getBody().array()).isEqualTo(new byte[]{0});


    }

    @Test
    public void isFullShouldBeFalse() {
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

        assertThat(message.getFrame().limit()).isEqualTo(data.length+1);
        assertThat(message.getFrame().capacity()).isEqualTo(data.length+1);
        assertThat(message.getFrame().position()).isZero();
        assertThat(message.getFrame().array()).isEqualTo(new byte[]{0,0,0,3,9,9,6});

        assertThat(message.getBody().limit()).isEqualTo(3);
        assertThat(message.getBody().capacity()).isEqualTo(3);
        assertThat(message.getBody().position()).isZero();
        assertThat(message.getBody().array()).isEqualTo(new byte[]{9,9,6});

    }


    @Test(expected = IllegalStateException.class)
    public void cannotContinueToReadIntoFullMessage() throws IOException {
        ByteBuffer wrapped = ByteBuffer.wrap(validFrame);
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
        ByteBuffer wrapped = ByteBuffer.wrap(validFrame);
        PostmanMessage message = new PostmanMessage();
        while(!message.isFull()) {
            message.read(ByteBuffer.wrap(new byte[]{wrapped.get()}));
        }

        assertThat(message.getFrame().limit()).isEqualTo(validFrame.length);
        assertThat(message.getFrame().capacity()).isEqualTo(validFrame.length);
        assertThat(message.getFrame().position()).isZero();
        assertThat(message.getFrame().array()).isEqualTo(validFrame);

        assertThat(message.getBody().limit()).isEqualTo(3);
        assertThat(message.getBody().capacity()).isEqualTo(3);
        assertThat(message.getBody().position()).isZero();
        assertThat(message.getBody().array()).isEqualTo(new byte[]{6,7,8});

    }

    @Test(expected = IllegalStateException.class)
    public void cannotContinueToReadIntoFullMessageFromBuffer() throws IOException {
        PostmanMessage message = validFromBody();
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

    @Test
    public void bufferShouldBeSame() {
        PostmanMessage msg = validFromBody();
        assertThat(msg.buffer()).isSameAs(msg.buffer());
    }


    private PostmanMessage validFromBody() {
        return new PostmanMessage(body);
    }


}