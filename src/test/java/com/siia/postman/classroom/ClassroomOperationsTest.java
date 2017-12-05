package com.siia.postman.classroom;

import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.ServerEvent;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClassroomOperationsTest {

    private ClassroomOperations classroomOperations;
    @Mock
    private PostmanDiscoveryService discoveryService;
    @Mock
    private PostmanServer postmanServer;
    @Mock
    private PostmanClient postmanClient;
    private PublishSubject<ServerEvent> serverEventStream;
    private TestScheduler computationScheduler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        serverEventStream = PublishSubject.create();
        computationScheduler = new TestScheduler();
        classroomOperations = new ClassroomOperations(postmanServer, discoveryService, postmanClient, computationScheduler);
    }

    @Test
    public void classNotStartedIfServerAndClientNotRunning() throws Exception {
        when(postmanServer.isRunning()).thenReturn(false);
        when(postmanClient.isConnected()).thenReturn(false);
        assertThat(classroomOperations.hasClassStarted()).isFalse();
    }

    @Test
    public void classStartedIfClientConnected() throws Exception {
        when(postmanServer.isRunning()).thenReturn(false);
        when(postmanClient.isConnected()).thenReturn(true);
        assertThat(classroomOperations.hasClassStarted()).isTrue();
    }

    @Test
    public void classStartedIfServerRunning() throws Exception {
        when(postmanServer.isRunning()).thenReturn(true);
        when(postmanClient.isConnected()).thenReturn(false);
        assertThat(classroomOperations.hasClassStarted()).isTrue();
    }

    @Test
    public void shouldObserveServerEventsOnComputationScheduler() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        classroomOperations.begin();
        serverEventStream.test().assertSubscribed();

    }

    @Test
     public void shouldStartServer() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        classroomOperations.begin();
        verify(postmanServer).serverStart();

    }

    @Test
    public void shouldStartBroadcastingOnceServerStarts() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        classroomOperations.begin();
        serverEventStream.onNext(ServerEvent.serverListening(11, "abc"));
        computationScheduler.triggerActions();
        verify(discoveryService).startServiceBroadcast(11, "abc");

    }

    @Test
    public void shouldStopBroadcastingOnErrorIfBroadcasting() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        when(discoveryService.isBroadcasting()).thenReturn(true);
        classroomOperations.begin();
        serverEventStream.onError(new RuntimeException());
        computationScheduler.triggerActions();
        verify(discoveryService).stopServiceBroadcast();

    }

    @Test
    public void shouldNotStopBroadcastingOnErrorIfNotBroadcasting() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        when(discoveryService.isBroadcasting()).thenReturn(false);
        classroomOperations.begin();
        serverEventStream.onError(new RuntimeException());
        computationScheduler.triggerActions();
        verify(discoveryService, never()).stopServiceBroadcast();

    }

    @Test
    public void shouldNotStopBroadcastingOnCompleteIfNotBroadcasting() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        when(discoveryService.isBroadcasting()).thenReturn(false);
        classroomOperations.begin();
        serverEventStream.onComplete();
        computationScheduler.triggerActions();
        verify(discoveryService, never()).stopServiceBroadcast();

    }

    @Test
    public void shouldStopBroadcastingOnCompleteIfBroadcasting() {
        when(postmanServer.getServerEventsStream()).thenReturn(serverEventStream);
        when(discoveryService.isBroadcasting()).thenReturn(true);
        classroomOperations.begin();
        serverEventStream.onComplete();
        computationScheduler.triggerActions();
        verify(discoveryService).stopServiceBroadcast();

    }

    @Test(expected = IllegalStateException.class)
    public void throwsExceptionIfTryingToEndClassWhenNotRunning() {
        when(postmanServer.isRunning()).thenReturn(false);
        when(postmanClient.isConnected()).thenReturn(false);
        classroomOperations.end();
    }

    @Test
    public void stopsServerButNotClient() {
        when(postmanServer.isRunning()).thenReturn(true);
        when(postmanClient.isConnected()).thenReturn(false);
        classroomOperations.end();
        verify(postmanServer).stopServer();
        verify(postmanClient, never()).disconnect();

    }

    @Test
    public void stopsClientButNotServer() {
        when(postmanServer.isRunning()).thenReturn(false);
        when(postmanClient.isConnected()).thenReturn(true);
        classroomOperations.end();
        verify(postmanServer, never()).stopServer();
        verify(postmanClient).disconnect();
    }

}