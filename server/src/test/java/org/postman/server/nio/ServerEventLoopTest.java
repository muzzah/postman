package org.postman.server.nio;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.postman.server.PostmanMessage;
import org.postman.server.PostmanServerEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Optional;

import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServerEventLoopTest {

    private ServerEventLoop serverEventLoop;

    @Mock
    private SelectorProvider selectorProvider;
    @Mock
    private ServerSocket serverSocket;
    @Mock
    private Socket socket;

    private InetSocketAddress bindAddress;
    private TestServerSocketChannel serverSocketChannel;
    private TestScheduler scheduler;
    private TestSubscriber<PostmanServerEvent> testSubscriber;
    private TestSelector serverSelector;
    private TestSocketChannel clientSocketChannel;
    @Mock
    private SelectionKey acceptSelectionKey;
    @Mock
    private SelectionKey clientSelectionKey;
    @Mock
    private NIOConnectionFactory nioConnectionFactory;
    @Mock
    private NIOConnection nioConnection;
    @Mock
    private PostmanMessage msg;


    @Before
    public void setup() {
        bindAddress = new InetSocketAddress("127.0.0.1", 23456);
        clientSocketChannel = new TestSocketChannel(selectorProvider, socket);
        serverSocketChannel = new TestServerSocketChannel(serverSocket, selectorProvider, clientSocketChannel);
        scheduler = new TestScheduler();
        testSubscriber = new TestSubscriber<>();
        serverEventLoop = new ServerEventLoop(selectorProvider, nioConnectionFactory, scheduler);
        serverSelector = new TestSelector(selectorProvider, msg, nioConnection, acceptSelectionKey, serverEventLoop);

    }



    @Test
    public void shouldSendErrorThroughAndShutdownIfProblemWhenBinding() throws IOException {
        setupBindingMockCalls();
        doThrow(IOException.class).when(nioConnectionFactory).bindServerSocket(serverSelector, serverSocketChannel, bindAddress);
        startLooping(false);
        testSubscriber.assertError(IOException.class);
    }

    @Test
    public void shouldBindServerSocketUsingFactory() throws IOException {
        setupBindingMockCalls();
        startLooping(false);
        verify(nioConnectionFactory).bindServerSocket(serverSelector, serverSocketChannel, bindAddress);
    }

    @Test
    public void shouldCloseConnectedClientsWhenShuttingDown() throws IOException {
        setupForAcceptingClient();
        setupBindingMockCalls();
        startLooping(true);

        verify(nioConnection).disconnect();
    }

    @Test
    public void shouldFireListeningEvent() throws IOException {
        setupBindingMockCalls();
        startLooping(true);
        testSubscriber.assertValueCount(1)
                .assertValue(PostmanServerEvent.serverListening(bindAddress.getPort(), bindAddress.getHostString()));
    }

    @Test
    public void shouldStopLoopingIfSelectorIsClosed() throws IOException {
        serverSelector.closeOnThirdSelect = true;
        setupBindingMockCalls();
        startLooping(true);
        verify(serverSocket).close();
        assertThat(serverSelector.closed).isTrue();
        assertThat(serverSocketChannel.closed).isTrue();

    }

    @Test
    public void shouldAcceptClientConnection() throws IOException {
        setupForAcceptingClient();
        setupBindingMockCalls();
        startLooping(true);
        testSubscriber.assertValueCount(2)
                .assertValueAt(1, serverEvent -> serverEvent.type() == PostmanServerEvent.Type.CLIENT_JOIN);

    }

    @Test
    public void shouldHandleErrorsWhenAcceptingClientConnection() throws IOException {
        serverSocketChannel.throwOnAccept = true;
        serverSelector.addSelectionKeyToReturn(acceptSelectionKey);
        when(acceptSelectionKey.isValid()).thenReturn(true);
        when(acceptSelectionKey.readyOps()).thenReturn(SelectionKey.OP_ACCEPT);
        when(nioConnectionFactory.acceptConnection(serverSocketChannel, serverSelector)).thenReturn(Optional.empty());
        setupBindingMockCalls();
        startLooping(true);
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldRemoveClientIfItsSelectionKeyIsInvalid() throws IOException {
        setupForAcceptingClient();

        serverSelector.addSelectionKeyToReturn(clientSelectionKey);
        when(clientSelectionKey.isValid()).thenReturn(false);

        setupBindingMockCalls();
        startLooping(true);
        testSubscriber.assertValueCount(3)
                .assertValueAt(2, PostmanServerEvent.clientDisconnected(nioConnection));
        verify(nioConnection).disconnect();
    }


    @Test
    public void shouldReadMessageFromClient() throws IOException {
        setupForAcceptingClient();

        serverSelector.addSelectionKeyToReturn(clientSelectionKey);
        when(clientSelectionKey.isValid()).thenReturn(true);
        when(clientSelectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);
        when(nioConnection.filledMessages()).thenReturn(Lists.newArrayList(msg));

        setupBindingMockCalls();
        startLooping(true);

        testSubscriber.assertValueCount(3)
                .assertValueAt(2, PostmanServerEvent.newMessage(msg, nioConnection));
    }

    @Test
    public void shouldHandleReadErrorFromClient() throws IOException {
        setupForAcceptingClient();

        serverSelector.addSelectionKeyToReturn(clientSelectionKey);
        when(clientSelectionKey.isValid()).thenReturn(true);
        when(clientSelectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);
        doThrow(IOException.class).when(nioConnection).read();

        setupBindingMockCalls();
        startLooping(true);

        verify(nioConnection).disconnect();
        testSubscriber.assertValueCount(3)
                .assertValueAt(2, PostmanServerEvent.clientDisconnected(nioConnection));
    }

    @Test
    public void shouldHandleWriteErrorFromClient() throws IOException {
        setupForAcceptingClient();
        serverSelector.addMessageOnSecondSelect = true;

        serverSelector.addSelectionKeyToReturn(clientSelectionKey);
        when(clientSelectionKey.isValid()).thenReturn(true);
        when(clientSelectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        doThrow(IOException.class).when(nioConnection).sendAnyPendingMessages();
        when(nioConnection.isConnected()).thenReturn(true);

        setupBindingMockCalls();
        startLooping(true);

        InOrder inOrder = inOrder(nioConnection);

        inOrder.verify(nioConnection).queueMessageToSend(msg);
        inOrder.verify(nioConnection).sendAnyPendingMessages();
        inOrder.verify(nioConnection).disconnect();

        testSubscriber.assertValueCount(3)
                .assertValueAt(2, PostmanServerEvent.clientDisconnected(nioConnection));

    }

    @Test
    public void shouldHandleHandleConnectionBeingInvalidBeforeSendingMessage() throws IOException {
        setupForAcceptingClient();
        serverSelector.addMessageOnSecondSelect = true;

        serverSelector.addSelectionKeyToReturn(clientSelectionKey);
        when(clientSelectionKey.isValid()).thenReturn(false);
        when(nioConnection.isConnected()).thenReturn(false);

        setupBindingMockCalls();
        startLooping(true);


        verify(nioConnection, never()).queueMessageToSend(msg);
        assertThat(serverSelector.wakeupCount).isZero();
    }

    @Test
    public void shouldWriteMessageForClient() throws IOException {
        setupForAcceptingClient();
        serverSelector.addMessageOnSecondSelect = true;

        serverSelector.addSelectionKeyToReturn(clientSelectionKey);
        when(clientSelectionKey.isValid()).thenReturn(true);
        when(clientSelectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(nioConnection.isConnected()).thenReturn(true);

        setupBindingMockCalls();
        startLooping(true);

        InOrder inOrder = inOrder(nioConnection);

        inOrder.verify(nioConnection).queueMessageToSend(msg);
        inOrder.verify(nioConnection).sendAnyPendingMessages();

        assertThat(serverSelector.wakeupCount).isEqualTo(1);

    }

    private void setupForAcceptingClient() {
        serverSelector.addSelectionKeyToReturn(acceptSelectionKey);
        when(acceptSelectionKey.isValid()).thenReturn(true);
        when(acceptSelectionKey.readyOps()).thenReturn(SelectionKey.OP_ACCEPT);
        when(nioConnectionFactory.acceptConnection(serverSocketChannel, serverSelector)).thenReturn(Optional.of(nioConnection));
        when(nioConnection.selectionKey()).thenReturn(clientSelectionKey);
    }

    private void startLooping(boolean noErrors) {
        serverEventLoop.startLooping(bindAddress).subscribe(testSubscriber);
        scheduler.triggerActions();
        if (noErrors) {
            testSubscriber.assertNoErrors()
                    .assertComplete();
        }
    }


    private void setupBindingMockCalls() throws IOException {
        serverSelector.closeOnThirdSelect = true;
        when(selectorProvider.openServerSocketChannel()).thenReturn(serverSocketChannel);
        when(selectorProvider.openSelector()).thenReturn(serverSelector);
    }
}