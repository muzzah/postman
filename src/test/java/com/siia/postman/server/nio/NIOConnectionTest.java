package com.siia.postman.server.nio;

import com.siia.postman.server.PostmanMessage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NIOConnectionTest {

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
    private ByteBuffer buffer;
    private Queue<PostmanMessage> readMessages;
    @Mock
    private SelectionKey key;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        buffer = ByteBuffer.allocate(100);
        readMessages = new ConcurrentLinkedQueue<>();
        connection = new NIOConnection(id, clientSocketChannel, provider, buffer, readMessages);
    }

    //1 socket read, 1 message read, no filled messages
    @Test
    public void singleSocketReadWithNoFilledMessages() throws IOException {
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
        assertThat(connection.filledMessages().size()).isZero();
        verify(msg, times(1)).read(buffer);
    }

    //1 socket read, 1 message read, 1 filled message
    @Test
    public void singleSocketReadWithOneFilledMessages() throws IOException {
        when(provider.get()).thenReturn(msg).thenReturn(msg2);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            when(msg.isFull()).thenReturn(true);
            return 100;
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
            when(msg.isFull()).thenReturn(true);
            return 100;
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
            when(msg.isFull()).thenReturn(true);
            when(msg2.isFull()).thenReturn(true);
            return 100;
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
            when(msg.isFull()).thenReturn(true);
            when(msg2.isFull()).thenReturn(true);
            return 100;
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
            when(msg.isFull()).thenReturn(true);
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
            when(msg.isFull()).thenReturn(true);
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
            when(msg2.isFull()).thenReturn(true);
            when(msg3.isFull()).thenReturn(true);
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
        when(msg.isFull()).thenReturn(true);
        when(msg2.isFull()).thenReturn(true);
        when(clientSocketChannel.read(buffer)).then(invocation -> {
            buffer.position(buffer.limit());
            return 100;
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

}