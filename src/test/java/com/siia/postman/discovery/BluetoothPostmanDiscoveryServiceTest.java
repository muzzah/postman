package com.siia.postman.discovery;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.reactivex.FlowableEmitter;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Copyright Siia 2018
 */
@RunWith(MockitoJUnitRunner.class)
public class BluetoothPostmanDiscoveryServiceTest {

    @Mock
    private BluetoothBroadcaster bluetoothBroadcaster;
    @Mock
    private BluetoothDiscoverer bluetoothDiscoverer;
    private TestScheduler scheduler;
    private BluetoothPostmanDiscoveryService service;

    @Before
    public void setup() {
        scheduler = new TestScheduler();
        service = new BluetoothPostmanDiscoveryService(bluetoothBroadcaster, bluetoothDiscoverer, scheduler);
    }


    @Test
    public void shouldStartBroadcast() throws UnknownHostException {
        TestSubscriber testSubscriber = service.startServiceBroadcast("name", 22, InetAddress.getLocalHost()).test();
        scheduler.triggerActions();
        verify(bluetoothBroadcaster).beginBroadcast(eq("name"), any(FlowableEmitter.class));
        testSubscriber.assertNoErrors()
                .assertComplete();
        verify(bluetoothBroadcaster).stopBroadcast();
    }

    @Test
    public void shouldNotifyOfErrorWhenBroadcasting() throws UnknownHostException {
        doThrow(RuntimeException.class).when(bluetoothBroadcaster).beginBroadcast(any(), any());
        TestSubscriber testSubscriber = service.startServiceBroadcast("name", 22, InetAddress.getLocalHost()).test();
        scheduler.triggerActions();
        testSubscriber.assertError(RuntimeException.class);
        verify(bluetoothBroadcaster).stopBroadcast();

    }

    @Test
    public void shouldNotifyOfErrorWhenDiscovering() throws UnknownHostException {
        doThrow(RuntimeException.class).when(bluetoothDiscoverer).findService(any(), any());
        TestSubscriber testSubscriber = service.discoverService("name", InetAddress.getLocalHost()).test();
        scheduler.triggerActions();
        testSubscriber.assertError(RuntimeException.class);
        verify(bluetoothDiscoverer).stopDiscovery();
    }

    @Test
    public void shouldStartDiscovery() throws UnknownHostException {
        TestSubscriber testSubscriber = service.discoverService("name", InetAddress.getLocalHost()).test();
        scheduler.triggerActions();
        verify(bluetoothDiscoverer).findService(eq("name"), any(FlowableEmitter.class));
        testSubscriber.assertNoErrors()
                .assertComplete();
        verify(bluetoothDiscoverer).stopDiscovery();
    }
}