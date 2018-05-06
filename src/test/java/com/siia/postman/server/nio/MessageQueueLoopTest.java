package com.siia.postman.server.nio;

import com.siia.postman.server.PostmanMessage;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(BlockJUnit4ClassRunner.class)
public class MessageQueueLoopTest {

    private MessageQueueLoop messageQueueLoop;
    private TestScheduler scheduler;
    @Mock
    private SelectorProvider selectorProvider;
    private TestSelector selector;
    @Mock
    private NIOConnection client;
    @Mock
    private NIOConnection client2;
    @Mock
    private SelectionKey selectionKey;

    private SocketChannel socketChannel;
    private SocketChannel socketChannel2;
    private TestSubscriber<MessageQueueEvent> readySubscriber;
    private TestObserver<MessageQueueEvent> msgSubscriber;
    @Mock
    private PostmanMessage postmanMessage;


    @Before
    public void setup() throws IOException {
        initMocks(this);

        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);

        readySubscriber = TestSubscriber.create();
        msgSubscriber = TestObserver.create();

        scheduler = new TestScheduler();
        messageQueueLoop = new MessageQueueLoop(scheduler, selectorProvider);
        selector = new TestSelector(selectorProvider, messageQueueLoop, postmanMessage, client, selectionKey);

        when(selectorProvider.openSelector()).thenReturn(selector);

    }

    @Test
    public void shouldStopLoopWhenShuttingDown() {
        selector.shutDownAfterSecondSelect = true;
        when(client.channel()).thenReturn(socketChannel);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        verify(client).destroy();
        assertThat(selector.closed).isTrue();
        readySubscriber.assertNoErrors()
                .assertComplete();
    }

    @Test
    public void shouldSendThroughReadyMessage() throws IOException {
        selector.close();
        TestSubscriber<MessageQueueEvent> testSubscriber = TestSubscriber.create();
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(testSubscriber);
        scheduler.triggerActions();
        testSubscriber.assertValue(MessageQueueEvent.ready())
                .assertValueCount(1);
    }

    @Test
    public void shouldEmitErrorIfSelectorCannotBeOpened() throws IOException {
        when(selectorProvider.openSelector()).thenThrow(IOException.class);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        scheduler.triggerActions();
        readySubscriber.assertError(IOException.class)
                .assertTerminated()
                .assertNoValues();
    }

    @Test
    public void shouldExitAndCompleteIfSelectorNotOpen() throws IOException {
        selector.close();
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        scheduler.triggerActions();

        readySubscriber.assertNoErrors()
                .assertComplete();
    }

    @Test
    public void shouldRegisterAnyClients() {
        when(client.channel()).thenReturn(socketChannel);
        selector.closeAfterRegistration = true;
        selector.expectedRegistration = 1;

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();
        assertThat(selector.wakeupCount).isEqualTo(1);
        assertThat(selector.registrationCount).isEqualTo(1);
        msgSubscriber.assertValueCount(1)
                .assertValue(MessageQueueEvent.clientRegistered(client));
        assertThat(selector.registrationOps.values()).containsOnly(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        verify(client).setSelectionKey(selectionKey);
    }

    @Test
    public void shouldRegisterAllClients() {
        when(client.channel()).thenReturn(socketChannel);
        when(client2.channel()).thenReturn(socketChannel2);
        selector.closeAfterRegistration = true;
        selector.expectedRegistration = 2;

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        messageQueueLoop.addClient(client2);
        scheduler.triggerActions();

        assertThat(selector.wakeupCount).isEqualTo(2);
        assertThat(selector.registrationCount).isEqualTo(2);
        msgSubscriber.assertValueCount(2)
                .assertValueAt(0 ,MessageQueueEvent.clientRegistered(client))
                .assertValueAt(1 ,MessageQueueEvent.clientRegistered(client2));
        assertThat(selector.registrationOps.values()).containsOnly(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        verify(client).setSelectionKey(selectionKey);
        verify(client2).setSelectionKey(selectionKey);

    }

    @Test
    public void shouldDestroyAndNotifyIfClientRegistrationFails() throws IOException {
        when(client.channel()).thenReturn(socketChannel);
        selector.throwAndCloseAfterRegistration = true;

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        msgSubscriber.assertValueCount(1)
                .assertValue(MessageQueueEvent.clientRegistrationFailed(client));
        verify(client).destroy();
    }

    @Test
    public void shouldCleanupConnectionWithInvalidKey() {
        selector.closeOnThirdSelect = true;
        selector.returnKeyAfterRegistration = true;
        when(client.channel()).thenReturn(socketChannel);
        when(client.selectionKey()).thenReturn(selectionKey);
        selector.selectedKeys.add(selectionKey);

        when(selectionKey.isValid()).thenReturn(false);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        msgSubscriber.assertValueCount(2)
                .assertValues(MessageQueueEvent.clientRegistered(client), MessageQueueEvent.clientUnregistered(client));

        verify(client).destroy();
    }

    @Test
    public void shouldReadAndNotifyOfMessage() throws IOException {
        selector.closeOnThirdSelect = true;
        selector.returnKeyAfterRegistration = true;
        when(client.channel()).thenReturn(socketChannel);
        when(client.selectionKey()).thenReturn(selectionKey);
        selector.selectedKeys.add(selectionKey);

        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);
        when(client.filledMessages()).thenReturn(Lists.newArrayList(postmanMessage));
        when(client.isValid()).thenReturn(true);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        msgSubscriber.assertValueCount(2)
                .assertValues(MessageQueueEvent.clientRegistered(client), MessageQueueEvent.messageReceived(client, postmanMessage));

        verify(client).read();
    }

    @Test
    public void shouldCleanupConnectionIfErrorOccursDuringReading() throws IOException {
        selector.closeOnThirdSelect = true;
        selector.returnKeyAfterRegistration = true;
        when(client.channel()).thenReturn(socketChannel);
        when(client.selectionKey()).thenReturn(selectionKey);
        selector.selectedKeys.add(selectionKey);

        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);
        doThrow(IOException.class).when(client).read();
        when(client.isValid()).thenReturn(false);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        msgSubscriber.assertValueCount(2)
                .assertValueAt(1, MessageQueueEvent.clientUnregistered(client));

        verify(client).destroy();
        verify(client, never()).filledMessages();
    }

    @Test
    public void shouldSendMessageForClient() throws IOException {
        selector.closeOnThirdSelect = true;
        selector.addMessageOnSecondSelect = true;

        when(client.channel()).thenReturn(socketChannel);
        when(client.selectionKey()).thenReturn(selectionKey);
        when(client.isValid()).thenReturn(true);
        selector.selectedKeys.add(selectionKey);

        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);

        when(client.sendMessage(postmanMessage)).thenReturn(true);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();


        InOrder order = inOrder(client);

        order.verify(client).setWriteInterest();
        order.verify(client).sendMessage(postmanMessage);
        order.verify(client).unsetWriteInterest();
        assertThat(selector.wakeupCount).isEqualTo(2);
    }

    @Test
    public void shouldNotSendMessageIfConnectionIsNotValid() {
        selector.closeOnThirdSelect = true;
        selector.addMessageOnSecondSelect = true;

        when(client.channel()).thenReturn(socketChannel);
        when(client.isValid()).thenReturn(false);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        readySubscriber.assertNoErrors()
                .assertComplete();

        verify(client, never()).setWriteInterest();
        assertThat(selector.wakeupCount).isEqualTo(1);
    }

    @Test
    public void shouldUnsetWriteInterestIfMessageQueueForConnectionEmpty() {
        selector.closeOnThirdSelect = true;
        selector.returnKeyAfterRegistration = true;

        when(client.channel()).thenReturn(socketChannel);

        selector.selectedKeys.add(selectionKey);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(selectionKey.isValid()).thenReturn(true);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        verify(client).unsetWriteInterest();
    }

    @Test
    public void shouldCleanupConnectionWhenErrorOccursDuringSendingMessage() throws IOException {
        selector.closeOnThirdSelect = true;
        selector.addMessageOnSecondSelect = true;

        when(client.channel()).thenReturn(socketChannel);

        selector.selectedKeys.add(selectionKey);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);
        when(selectionKey.isValid()).thenReturn(true);
        when(client.sendMessage(postmanMessage)).thenThrow(IOException.class);

        when(client.isValid()).thenReturn(true, false);
        when(client.selectionKey()).thenReturn(selectionKey);

        messageQueueLoop.messageQueueEventsStream().subscribe(msgSubscriber);
        messageQueueLoop.startMessageQueueLoop()
                .subscribe(readySubscriber);
        messageQueueLoop.addClient(client);
        scheduler.triggerActions();

        verify(client).destroy();
        verify(client, never()).unsetWriteInterest();
        msgSubscriber.assertValueCount(2)
                .assertValueAt(1, MessageQueueEvent.clientUnregistered(client));
    }




}