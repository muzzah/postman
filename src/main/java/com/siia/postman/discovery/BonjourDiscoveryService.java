package com.siia.postman.discovery;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.siia.commons.core.constants.Constants;
import com.siia.commons.core.log.Logcat;
import com.siia.commons.core.string.Strings;

import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import javax.inject.Named;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;

import static com.siia.commons.core.concurrency.ConcurrencyUtils.awaitLatch;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Copyright Siia 2018
 */

public class BonjourDiscoveryService implements PostmanDiscoveryService {

    private static final String TAG = Logcat.getTag();
    private JmDNS jmdns;
    private CountDownLatch broadcastLatch;
    private CountDownLatch discoveryLatch;
    private final Scheduler io;
    private SiiaServiceListener siiaServiceListener;

    public BonjourDiscoveryService(@Named("io") Scheduler io) {
        this.io = io;
    }

    @Override
    public Flowable<PostmanBroadcastEvent> startServiceBroadcast(@NonNull String serviceName, int port, @NonNull InetAddress hostAddress) {
        return Flowable.<PostmanBroadcastEvent>fromPublisher(
                subscriber -> {
                    try {
                        broadcastLatch = new CountDownLatch(1);
                        jmdns = JmDNS.create(hostAddress, "SiiaBroadcaster");
                        ServiceInfo serviceInfo = ServiceInfo.create(Constants.SERVICE_TYPE, serviceName, port, 100, 100, true,
                                Strings.EMPTY_STRING);
                        jmdns.registerService(serviceInfo);
                        Logcat.i(TAG, "Registered service %s port=%d address=%s", serviceName, port, hostAddress.toString());
                        subscriber.onNext(PostmanBroadcastEvent.broadcastStarted());
                        awaitLatch(broadcastLatch);
                        Logcat.i(TAG, "Finished Service Broadcast", serviceName, port, hostAddress.toString());
                        subscriber.onComplete();
                    } catch (Throwable e) {
                        subscriber.onError(e);
                    } finally {
                        if (nonNull(jmdns)) {
                            jmdns.unregisterAllServices();
                            closeJmdnsQuietly();
                        }
                    }
                }).subscribeOn(io);
    }


    @Override
    public void stopServiceBroadcast() {
        if (nonNull(broadcastLatch)) {
            broadcastLatch.countDown();
        }
    }

    @SuppressLint("VisibleForTests")
    @Override
    public Flowable<PostmanDiscoveryEvent> discoverService(@NonNull String serviceName, @NonNull InetAddress addressToSearchOn) {
        return Flowable.<PostmanDiscoveryEvent>fromPublisher(
                subscriber -> {
                    try {
                        jmdns = JmDNS.create(addressToSearchOn, "SiiaDiscoverer");
                        subscriber.onNext(PostmanDiscoveryEvent.started());

                        Logcat.i(TAG, "Searching for %s on %s", serviceName, addressToSearchOn.toString());
                        discoveryLatch = new CountDownLatch(1);
                        siiaServiceListener = new SiiaServiceListener(subscriber);
                        jmdns.addServiceListener(Constants.SERVICE_TYPE, siiaServiceListener);
                        awaitLatch(discoveryLatch);
                        subscriber.onComplete();
                    } catch (IOException e) {
                        subscriber.onError(e);
                    } finally {
                        if (nonNull(jmdns)) {
                            jmdns.removeServiceListener(Constants.SERVICE_TYPE, siiaServiceListener);
                            closeJmdnsQuietly();
                        }
                    }
                }
        ).subscribeOn(io);
    }

    @Override
    public void stopDiscovery() {
        if (nonNull(discoveryLatch)) {
            discoveryLatch.countDown();
        }
    }

    private void closeJmdnsQuietly() {
        if (isNull(jmdns)) {
            return;
        }

        try {
            jmdns.close();
        } catch (IOException e) {
            Logcat.w(TAG, "Problem when closing JMDNS", e);
            //Ignore
        }
    }

    private class SiiaServiceListener implements ServiceListener {
        private final Subscriber<? super PostmanDiscoveryEvent> subscriber;

        SiiaServiceListener(Subscriber<? super PostmanDiscoveryEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void serviceAdded(ServiceEvent event) {
            Logcat.d(TAG, "Service Added : type=%s name=%s", event.getType(), event.getName());
            ServiceInfo serviceInfo = event.getDNS().getServiceInfo(event.getType(), event.getName());

            if(isNull(serviceInfo)) {
                Logcat.w(TAG, "Empty service info returned, requesting info");
                event.getDNS().requestServiceInfo(event.getType(), event.getName(), true);
                return;
            }
            Logcat.v(TAG, "domain=%s app=%s addresses=%s name=%s port=%d urls=%s", serviceInfo.getDomain(), serviceInfo.getApplication(), Arrays.toString(serviceInfo.getInetAddresses()),
                    serviceInfo.getName(), serviceInfo.getPort(), Arrays.toString(serviceInfo.getURLs()));

            parseAndNotifyFound(serviceInfo);

        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            Logcat.d(TAG, "Service Removed : %s", event.toString());
            subscriber.onNext(PostmanDiscoveryEvent.lost());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            Logcat.d(TAG, "Service Resolved : %s", event.toString());
            ServiceInfo serviceInfo = event.getInfo();
            parseAndNotifyFound(serviceInfo);
        }

        //Synchronize this so onNext calls are serialised
        private synchronized void parseAndNotifyFound(ServiceInfo serviceInfo) {
            Inet4Address[] addresses = serviceInfo.getInet4Addresses();
            InetAddressServiceDetails serviceDetails = new InetAddressServiceDetails(addresses[0], serviceInfo.getPort());
            Logcat.v(TAG, "address=%s port=%d", Arrays.toString(addresses), serviceInfo.getPort());
            subscriber.onNext(new PostmanDiscoveryEvent(serviceDetails));
        }
    }
}
