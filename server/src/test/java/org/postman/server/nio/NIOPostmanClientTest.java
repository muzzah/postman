package org.postman.server.nio;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.postman.server.PostmanClientEvent;
import org.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NIOPostmanClientTest {
    private NIOPostmanClient client;
    @Mock
    private SelectorProvider selectorProvider;
    @Mock
    private NIOConnectionFactory nioConnectionFactory;
    @Mock
    private SocketChannel socketChannel;
    @Mock
    private InetAddress serverAddress;
    private int port = 2020;
    private TestScheduler scheduler;
    private TestSelector selector;
    @Mock
    private NIOConnection connection;
    @Mock
    private SelectionKey selectionKey;
    @Mock
    private PostmanMessage msg;
    @Mock
    private PostmanMessage msg2;

    @Before
    public void setup() {
        scheduler = new TestScheduler();
        selector = new TestSelector(selectorProvider);
        client = new NIOPostmanClient(scheduler, selectorProvider, nioConnectionFactory);
    }

    @Test
    public void shouldEmitErrorIfProblemWhenConnecting() throws IOException {
        when(selectorProvider.openSelector()).thenReturn(selector);
        when(nioConnectionFactory.connectToServer(selector, socketChannel, serverAddress, port)).thenThrow(IOException.class);
        TestSubscriber testSubscriber = client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        testSubscriber.assertError(IOException.class);
        assertThat(selector.closed).isTrue();
    }

    @Test
    public void shouldEmitConnectedEvent() throws IOException {
        setupMocksForConnecting();
        TestSubscriber testSubscriber = client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        testSubscriber.assertNoErrors()
                .assertComplete()
                .assertValueCount(1)
                .assertValue(PostmanClientEvent.isConnectedEvent());
    }

    @Test
    public void shouldCloseConnectionUponExitingLoop() throws IOException {
        setupMocksForConnecting();
        TestSubscriber testSubscriber = client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        testSubscriber.assertNoErrors()
                .assertComplete();
        assertThat(selector.closed).isTrue();
        verify(connection).disconnect();
    }

    @Test
    public void shouldRaiseErrorAndDisconnectIfSelectionKeyBecomesInvalid() throws IOException {
        setupMocksForConnecting();
        selector.addSelectionKeyToReturn(selectionKey);
        when(selectionKey.isValid()).thenReturn(false);
        TestSubscriber testSubscriber = client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        testSubscriber.assertError(IllegalStateException.class);
        assertThat(selector.closed).isTrue();
        verify(connection).disconnect();
    }

    @Test
    public void shouldReadMessagesAndNotify() throws IOException {
        setupMocksForConnecting();
        selector.addSelectionKeyToReturn(selectionKey);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);
        when(connection.filledMessages()).thenReturn(Lists.newArrayList(msg, msg2));
        TestSubscriber testSubscriber = client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        testSubscriber.assertNoErrors()
                .assertValueCount(3)
                .assertValueAt(1, PostmanClientEvent.newMessage(msg))
                .assertValueAt(2, PostmanClientEvent.newMessage(msg2))
                .assertComplete();
        verify(connection).read();

    }

    @Test
    public void shouldSendMessages() throws IOException {
        setupMocksForConnecting();
        selector.addSelectionKeyToReturn(selectionKey);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        TestSubscriber testSubscriber = client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        testSubscriber.assertNoErrors()
                .assertComplete();
        verify(connection).sendAnyPendingMessages();

    }

    @Test
    public void shouldAddMessageToConnectionAndWakeupSelector() throws IOException {
        setupMocksForConnecting();
        client.connect(socketChannel, serverAddress, port).test();
        scheduler.triggerActions();
        client.sendMessage(msg);
        assertThat(selector.wakeupCount).isEqualTo(1);
        verify(connection).queueMessageToSend(msg);
    }

    private void setupMocksForConnecting() throws IOException {
        selector.closeOnThirdSelect = true;
        when(selectorProvider.openSelector()).thenReturn(selector);
        when(nioConnectionFactory.connectToServer(selector, socketChannel, serverAddress, port)).thenReturn(connection);
        when(connection.isConnected()).thenReturn(true);
    }

}