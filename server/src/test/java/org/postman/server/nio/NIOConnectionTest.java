package org.postman.server.nio;

import com.siia.commons.core.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NIOConnectionTest {

    private static final int BUFFER_1_SIZE = 100;
    private static final int BUFFER_2_SIZE = 80;

    private NIOConnection connection;
    private UUID id = UUID.randomUUID();

    @Mock
    private SocketChannel clientSocketChannel;
    @Mock
    private Provider<PostmanMessage> provider;
    @Mock
    private PostmanMessage msg;
    @Mock
    private PostmanMessage msg2;
    @Mock
    private PostmanMessage msg3;
    @Mock
    private SelectionKey key;
    @Mock
    private SelectorProvider selectorProvider;
    @Mock
    private Socket socket;
    @Mock
    private Selector selector;

    private ByteBuffer buffer2;
    private ByteBuffer buffer;


    @Before
    public void setUp() {
        initMocks(this);
        buffer = ByteBuffer.allocate(BUFFER_1_SIZE);
        buffer2 = ByteBuffer.allocate(BUFFER_2_SIZE);
        connection = new NIOConnection(id, clientSocketChannel, provider, buffer, key);
    }

    //1 socket read, 1 message read, no filled messages
    @Test
    public void singleSocketReadWithNoFilledMessages() throws IOException {
        when(provider.get()).thenReturn(msg);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        }).thenReturn(0);
        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        });
        connection.read();
        assertThat(connection.filledMessages().size()).isZero();
        verify(msg, times(1)).read(buffer);
    }

    //1 socket read, 1 message read, 1 filled message
    @Test
    public void singleSocketReadWithOneFilledMessages() throws IOException {
        when(provider.get()).thenReturn(msg).thenReturn(msg2);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isInitialised()).thenReturn(true);
            return buffer.limit();
        }).thenReturn(0);
        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return true;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg);
        verify(msg, times(1)).read(buffer);
        verify(msg2, never()).read(buffer);


    }

    //1 socket read, 1+1 message read, 1 filled message
    @Test
    public void singleSocketReadWithMultipleMessageReads() throws IOException {
        when(provider.get()).thenReturn(msg).thenReturn(msg2);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isInitialised()).thenReturn(true);
            return buffer.limit();
        }).thenReturn(0);
        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit() / 2);
            return true;
        });
        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg);
        verify(msg, times(1)).read(buffer);
        verify(msg2, times(1)).read(buffer);


    }

    //1 socket read, 1+1 message read, 2 filled message
    @Test
    public void singleSocketReadWithMultipleMessages() throws IOException {
        when(provider.get()).thenReturn(msg).thenReturn(msg2).thenReturn(msg3);

        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isInitialised()).thenReturn(true);
            when(msg2.isInitialised()).thenReturn(true);
            return buffer.limit();
        }).thenReturn(0);
        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit() / 2);
            return true;
        });
        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return true;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg, msg2);
        verify(msg, times(1)).read(buffer);
        verify(msg2, times(1)).read(buffer);


    }

    //2 socket read, (1)+(1+1) message read, 2 filled message
    @Test
    public void multipleSocketReadWithFilledMessages() throws IOException {
        when(provider.get()).thenReturn(msg).thenReturn(msg2).thenReturn(msg3);

        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isInitialised()).thenReturn(true);
            when(msg2.isInitialised()).thenReturn(true);
            return buffer.limit();
        }).then(invocation -> {
            buffer.position(buffer.limit());
            return 50;
        }).thenReturn(0);

        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        }).then(invocation -> {
            buffer.position(buffer.limit()/2);
            return true;
        });

        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return true;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg, msg2);
        verify(msg, times(2)).read(buffer);
        verify(msg2, times(1)).read(buffer);


    }

    //2 socket read, (1)+(1+1) message read, 2 filled message
    @Test
    public void multipleReadInvocationTest() throws IOException {
        when(provider.get()).thenReturn(msg);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return 100;
        }).thenReturn(0);

        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        });

        connection.read();
        assertThat(connection.filledMessages()).isEmpty();
        verify(msg, times(1)).read(buffer);
        reset(msg);


        when(provider.get()).thenReturn(msg2);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isInitialised()).thenReturn(true);
            return 200;
        }).thenReturn(0);

        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit()/2);
            return true;
        });
        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg);
        verify(msg, times(1)).read(buffer);
        verify(msg2, times(1)).read(buffer);


    }

    //3 socket read, (1)+(1+1)(1+1) message read, 3 filled message
    @Test
    public void multipleReadInvocationTestLonger() throws IOException {
        when(provider.get()).thenReturn(msg);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        }).thenReturn(0);

        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        });

        connection.read();
        assertThat(connection.filledMessages()).isEmpty();
        verify(msg, times(1)).read(buffer);
        reset(msg);


        when(provider.get()).thenReturn(msg2);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isInitialised()).thenReturn(true);
            return 200;
        }).thenReturn(0);

        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit()/2);
            return true;
        });
        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return false;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg);
        verify(msg, times(1)).read(buffer);
        verify(msg2, times(1)).read(buffer);
        reset(msg2);

        when(provider.get()).thenReturn(msg3).thenReturn(mock(PostmanMessage.class));
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg2.isInitialised()).thenReturn(true);
            when(msg3.isInitialised()).thenReturn(true);
            return 500;
        }).thenReturn(0);

        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit()/4);
            return true;
        });
        when(msg3.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return true;
        });
        connection.read();

        assertThat(connection.filledMessages()).containsExactly(msg2, msg3);
        verify(msg2, times(1)).read(buffer);
        verify(msg3, times(1)).read(buffer);
    }

    @Test
    public void filledMessagesThatAreReadShouldBeExcludedNextTime() throws IOException {
        when(provider.get()).thenReturn(msg).thenReturn(msg2).thenReturn(msg3);
        when(msg.isInitialised()).thenReturn(true);
        when(msg2.isInitialised()).thenReturn(true);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        }).thenReturn(0);
        when(msg.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit() / 2);
            return true;
        });
        when(msg2.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return true;
        });
        connection.read();
        assertThat(connection.filledMessages()).containsExactly(msg, msg2);
        assertThat(connection.filledMessages()).isEmpty();


    }

    @Test
    public void shouldSetWriteInterestIfValidKey() {
        when(key.isValid()).thenReturn(true);
        when(key.interestOps()).thenReturn(SelectionKey.OP_READ);
        connection.setWriteInterest();
        verify(key).interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    @Test
    public void shouldNotSetWriteInterestIfKeyInvalid() {
        when(key.isValid()).thenReturn(false);
        connection.setWriteInterest();
        verify(key, never()).interestOps(anyInt());
    }

    @Test
    public void shouldUnsetWriteInterestIfValidKey() {
        when(key.isValid()).thenReturn(true);
        when(key.interestOps()).thenReturn(SelectionKey.OP_READ);
        connection.unsetWriteInterest();
        verify(key).interestOps(SelectionKey.OP_READ & ~SelectionKey.OP_WRITE);
    }

    @Test
    public void shouldNotUnsetWriteInterestIfKeyInvalid() {
        when(key.isValid()).thenReturn(false);
        connection.unsetWriteInterest();
        verify(key, never()).interestOps(anyInt());
    }

    @Test
    public void shouldDisconnectAndClearState() throws IOException {
        clientSocketChannel = new TestSocketChannel(selectorProvider, socket);
        connection = new NIOConnection(id, clientSocketChannel, provider, buffer, key);
        connection.disconnect();
        verify(key).cancel();
        assertThat(((org.postman.server.nio.TestSocketChannel)clientSocketChannel).closed).isTrue();
        verify(socket).close();

    }

    @Test
    public void shouldSetWriteInterestWhenAddingMsgToSend() {
        when(key.isValid()).thenReturn(true);
        when(clientSocketChannel.isConnected()).thenReturn(true);
        when(key.selector()).thenReturn(selector);
        connection.queueMessageToSend(msg);
        verify(key).interestOps(SelectionKey.OP_WRITE);

    }

    @Test
    public void shouldSendMessage() throws IOException {
        when(key.isValid()).thenReturn(true);
        when(msg.getFrame()).thenReturn(buffer);
        when(key.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(key.selector()).thenReturn(selector);
        when(clientSocketChannel.isConnected()).thenReturn(true);
        when(clientSocketChannel.write(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        });

        connection.queueMessageToSend(msg);
        connection.sendAnyPendingMessages();

        verify(key).interestOps(SelectionKey.OP_WRITE);
        verify(key).interestOps(0);
        verify(selector).wakeup();
        verify(clientSocketChannel).write(buffer);

    }

    @Test
    public void shouldSendMessages() throws IOException {
        when(key.isValid()).thenReturn(true);
        when(msg.getFrame()).thenReturn(buffer);
        when(key.selector()).thenReturn(selector);
        when(msg2.getFrame()).thenReturn(buffer2);
        when(key.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(clientSocketChannel.isConnected()).thenReturn(true);

        when(clientSocketChannel.write(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        });

        when(clientSocketChannel.write(buffer2)).then(invocation -> {
            buffer2.position(buffer2.limit());
            return buffer2.limit();
        });

        connection.queueMessageToSend(msg);
        connection.queueMessageToSend(msg2);
        connection.sendAnyPendingMessages();

        buffer.rewind();
        buffer2.rewind();

        verify(key, times(2)).interestOps(SelectionKey.OP_WRITE);
        verify(key).interestOps(0);
        verify(clientSocketChannel).write(buffer);
        verify(clientSocketChannel).write(buffer2);

    }


    @Test
    public void shouldSendMessageOverMultipleInvocations() throws IOException {
        when(key.isValid()).thenReturn(true);
        when(msg.getFrame()).thenReturn(buffer);
        when(key.selector()).thenReturn(selector);
        when(key.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(clientSocketChannel.isConnected()).thenReturn(true);

        when(clientSocketChannel.write(buffer)).then(invocation -> {
            buffer.position(buffer.limit() / 2);
            return buffer.limit() / 2;
        }).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        });


        connection.queueMessageToSend(msg);
        connection.sendAnyPendingMessages();

        verify(key).interestOps(0);
        verify(clientSocketChannel, times(2)).write(buffer);

    }

    @Test
    public void shouldSendMultipleMessageOverMultipleInvocationsInOrder() throws IOException {
        when(key.isValid()).thenReturn(true);
        when(msg.getFrame()).thenReturn(buffer);
        when(msg2.getFrame()).thenReturn(buffer2);
        when(key.selector()).thenReturn(selector);
        when(key.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(clientSocketChannel.isConnected()).thenReturn(true);

        when(clientSocketChannel.write(buffer)).then(invocation -> {
            buffer.position(buffer.limit() / 2);
            return buffer.limit() / 2;
        }).then(invocation -> {
            buffer.position(buffer.limit());
            return buffer.limit();
        });

        when(clientSocketChannel.write(buffer2)).then(invocation -> {
            buffer2.position(buffer2.limit());
            return buffer2.limit();
        });


        connection.queueMessageToSend(msg);
        connection.queueMessageToSend(msg2);
        connection.sendAnyPendingMessages();

        verify(key).interestOps(0);
        InOrder inOrder = inOrder(clientSocketChannel);

        buffer.rewind();
        inOrder.verify(clientSocketChannel).write(buffer);
        buffer.position(buffer.limit() / 2);
        inOrder.verify(clientSocketChannel).write(buffer);
        buffer2.rewind();
        inOrder.verify(clientSocketChannel).write(buffer2);

    }

}