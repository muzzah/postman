package org.postman.server.nio;

import com.siia.commons.core.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.postman.server.Connection;
import org.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class NIOConnectionFactoryTest {
    private NIOConnectionFactory nioConnectionFactory;
    @Mock
    private Provider<PostmanMessage> messageProvider;
    private TestSelector selector;
    private TestServerSocketChannel serverSocketChannel;
    private InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", 22222);
    @Mock
    private SelectorProvider selectorProvider;
    @Mock
    private ServerSocket serverSocket;
    private TestSocketChannel sockChannel;
    @Mock
    private Socket socket;

    @Before
    public void setup() {
        selector = new TestSelector(selectorProvider);
        sockChannel = new TestSocketChannel(selectorProvider, socket);
        serverSocketChannel = new TestServerSocketChannel(serverSocket, selectorProvider, sockChannel);
        nioConnectionFactory = new NIOConnectionFactory(messageProvider);
    }



    @Test
    public void shouldSetPerfSettingsWhenBindingSocket() throws IOException {
        nioConnectionFactory.bindServerSocket(selector, serverSocketChannel, bindAddress);
        verify(serverSocket).setPerformancePreferences(NIOConnectionFactory.CONNECTION_TIME_PREFERENCE,
                NIOConnectionFactory.LATENCY_PREFERENCE, NIOConnectionFactory.BANDWIDTH_PREFERENCE);

    }


    @Test
    public void shouldBindToGivenAddress() throws IOException {
        nioConnectionFactory.bindServerSocket(selector, serverSocketChannel, bindAddress);
        verify(serverSocket).bind(bindAddress);
    }

    @Test
    public void shouldConfigureSocketToBlocking() throws IOException {
        nioConnectionFactory.bindServerSocket(selector, serverSocketChannel, bindAddress);
        assertThat(serverSocketChannel.blocking).isFalse();
    }

    @Test
    public void shouldRegisterSocketForAccepting() throws IOException {
        nioConnectionFactory.bindServerSocket(selector, serverSocketChannel, bindAddress);
        assertThat(selector.registrationOps.get(serverSocketChannel)).isEqualTo(SelectionKey.OP_ACCEPT);
        assertThat(selector.registrationCount).isEqualTo(1);
    }
}