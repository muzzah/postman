package com.siia.postman.server.nio;

import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.ServerEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;

import javax.inject.Provider;

import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subscribers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServerEventLoopTest {

    private ServerEventLoop serverEventLoop;

    @Mock
    private MessageQueueLoop messageRouter;
    @Mock
    private Provider<PostmanMessage> messageProvider;
    @Mock
    private SelectorProvider selectorProvider;
    @Mock
    private ServerSocket serverSocket;
    @Mock
    private Socket socket;

    private PublishSubject<MessageQueueEvent> messageQueueStream;
    private InetSocketAddress bindAddress;
    private TestServerSocketChannel serverSocketChannel;
    private PublishProcessor<MessageQueueEvent> mainMessageQueueStream;
    private TestScheduler scheduler;
    private TestSubscriber<ServerEvent> testSubscriber;
    private TestSelector selector;
    private TestSocketChannel clientSocketChannel;
    @Mock
    private SelectionKey selectionKey;


    @Before
    public void setup() {
        bindAddress = new InetSocketAddress("127.0.0.1", 23456);
        messageQueueStream = PublishSubject.create();
        mainMessageQueueStream = PublishProcessor.create();
        clientSocketChannel = new TestSocketChannel(selectorProvider, socket);
        serverSocketChannel = new TestServerSocketChannel(serverSocket, selectorProvider, clientSocketChannel);
        selector = new TestSelector(selectorProvider);
        scheduler = new TestScheduler();
        testSubscriber = new TestSubscriber<>();
        serverEventLoop = new ServerEventLoop(messageRouter, scheduler, messageProvider, scheduler, selectorProvider);

        when(messageRouter.messageQueueEventsStream()).thenReturn(messageQueueStream);
        when(messageRouter.startMessageQueueLoop()).thenReturn(mainMessageQueueStream);
        serverEventLoop.getServerEventsStream().observeOn(scheduler).subscribe(testSubscriber);
    }


    @Test
    public void shouldSetPerfSettingsWhenBindingSocket() throws IOException {
        setupBindingMockCalls();
        startLooping(true);

        verify(serverSocket).setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                Connection.LATENCY_PREFERENCE, Connection.BANDWIDTH_PREFERENCE);

    }


    @Test
    public void shouldBindToGivenAddress() throws IOException {
        setupBindingMockCalls();
        startLooping(true);

        verify(serverSocket).bind(bindAddress);
    }

    @Test
    public void shouldConfigureSocketToBlocking() throws IOException {
        setupBindingMockCalls();
        startLooping(true);

        assertThat(serverSocketChannel.blocking).isFalse();
    }

    @Test
    public void shouldRegisterSocketForAccepting() throws IOException {
        setupBindingMockCalls();
        startLooping(true);

        assertThat(selector.registrationOps.get(serverSocketChannel)).isEqualTo(SelectionKey.OP_ACCEPT);
        assertThat(selector.registrationCount).isEqualTo(1);
    }

    @Test
    public void shouldSendErrorThroughAndShutdownIfProblemWhenBinding() throws IOException {
        setupBindingMockCalls();
        doThrow(IOException.class).when(serverSocket).bind(any());
        startLooping(false);
        testSubscriber.assertError(IOException.class);
        verify(messageRouter).stopLoop();
        verify(messageRouter).shutdown();
        verify(serverSocket).close();
        assertThat(serverSocketChannel.closed).isTrue();


    }

    @Test
    public void shouldFireListeningEvent() throws IOException {
        setupBindingMockCalls();
        startLooping(true);
        testSubscriber.assertValueCount(1)
                .assertValue(ServerEvent.serverListening(bindAddress.getPort(), bindAddress.getHostString()));
    }

    @Test
    public void shouldStopLoopingIfSelectorIsClosed() throws IOException {
        selector.closeOnThirdSelect = true;
        setupBindingMockCalls();
        startLooping(true);
        assertLoopEnded();

    }

    private void assertLoopEnded() throws IOException {
        InOrder inOrder = Mockito.inOrder(messageRouter, serverSocket);
        inOrder.verify(messageRouter).stopLoop();
        inOrder.verify(serverSocket).close();
        inOrder.verify(messageRouter).shutdown();
        assertThat(selector.closed).isTrue();
        assertThat(serverSocketChannel.closed).isTrue();
    }

    @Test
    public void shouldShutDownLoopIfMainMsgQueueErrors() {
        serverEventLoop.startLooping(bindAddress);
        mainMessageQueueStream.onError(new RuntimeException());
        scheduler.triggerActions();
        verify(messageRouter).stopLoop();
        verify(messageRouter).shutdown();
        testSubscriber.assertError(RuntimeException.class)
                .assertTerminated();

    }

    @Test
    public void shouldShutDownLoopIfMainMsgQueueCompletes() {
        serverEventLoop.startLooping(bindAddress);
        mainMessageQueueStream.onComplete();
        scheduler.triggerActions();
        verify(messageRouter).stopLoop();
        verify(messageRouter).shutdown();
        testSubscriber.assertComplete();

    }

    @Test
    public void shouldShutDownLoopIfMsgStreamCompletes() {
        serverEventLoop.startLooping(bindAddress);
        messageQueueStream.onComplete();
        scheduler.triggerActions();
        verify(messageRouter).stopLoop();
        verify(messageRouter).shutdown();
        testSubscriber.assertComplete();

    }

    @Test
    public void shouldShutDownLoopIfMsgStreamErrors() {
        serverEventLoop.startLooping(bindAddress);
        messageQueueStream.onError(new IllegalArgumentException());
        scheduler.triggerActions();
        verify(messageRouter).stopLoop();
        verify(messageRouter).shutdown();
        testSubscriber.assertError(IllegalArgumentException.class)
                .assertTerminated();

    }

    @Test
    public void shouldAcceptClientConnection() throws IOException {
        selector.returnKeyAfterRegistration = true;
        clientSocketChannel.blocking = true;
        selector.selectedKeys.add(selectionKey);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_ACCEPT);

        setupBindingMockCalls();
        startLooping(true);
        verify(socket).setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                Connection.LATENCY_PREFERENCE,Connection.BANDWIDTH_PREFERENCE);
        verify(socket).setKeepAlive(true);
        assertThat(clientSocketChannel.blocking).isFalse();
        verify(messageRouter).addClient(any(NIOConnection.class));

    }

    @Test
    public void shouldHandleErrorsWhenAcceptingClientConnection() throws IOException {
        selector.returnKeyAfterRegistration = true;
        serverSocketChannel.throwOnAccept = true;
        selector.selectedKeys.add(selectionKey);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_ACCEPT);

        setupBindingMockCalls();
        startLooping(true);
        verifyZeroInteractions(socket);
        verify(messageRouter, never()).addClient(any(NIOConnection.class));

    }

    private void startLooping(boolean noErrors) {
        serverEventLoop.startLooping(bindAddress);
        mainMessageQueueStream.onNext(MessageQueueEvent.ready());
        scheduler.triggerActions();
        if(noErrors) {
            testSubscriber.assertNoErrors()
                    .assertComplete();
        }
    }


    private void setupBindingMockCalls() throws IOException {
        selector.closeOnThirdSelect = true;
        when(selectorProvider.openServerSocketChannel()).thenReturn(serverSocketChannel);
        when(selectorProvider.openSelector()).thenReturn(selector);
    }
}