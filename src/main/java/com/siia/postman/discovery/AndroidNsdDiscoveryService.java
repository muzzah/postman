package com.siia.postman.discovery;

import android.annotation.SuppressLint;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.RegistrationListener;
import android.net.nsd.NsdServiceInfo;
import android.support.annotation.NonNull;
import android.util.Log;

import com.siia.commons.core.constants.Constants;
import com.siia.commons.core.log.Logcat;

import org.reactivestreams.Subscriber;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;

public class AndroidNsdDiscoveryService implements PostmanDiscoveryService {


    private static final String TAG = Logcat.getTag();

    private final NsdManager nsdManager;
    private final AtomicBoolean broadcastActive;
    private final AtomicBoolean discoveryActive;
    private final Scheduler io;
    private RegistrationListener serviceRegistrationListener;
    private ServiceDiscoveryListener serviceDiscoveryListener;

    private CountDownLatch broadcastLatch;
    private CountDownLatch discoveryLatch;


    public AndroidNsdDiscoveryService(NsdManager ndsManager, Scheduler io) {
        this.nsdManager = ndsManager;
        this.io = io;
        this.broadcastActive = new AtomicBoolean(false);
        this.discoveryActive = new AtomicBoolean(false);
    }

    @Override
    public Flowable<PostmanBroadcastEvent> startServiceBroadcast(@NonNull String serviceName, int port, @NonNull InetAddress hostAddress) {
        return Flowable.<PostmanBroadcastEvent>fromPublisher(subscriber -> {

            if (broadcastActive.compareAndSet(false, true)) {
                serviceRegistrationListener = new ServiceRegistrationListener(subscriber);
                NsdServiceInfo postmanServiceInfo = new NsdServiceInfo();

                // The name is subject to change based on conflicts
                // with other services advertised on the same network.
                postmanServiceInfo.setServiceName(serviceName);
                postmanServiceInfo.setServiceType(Constants.SERVICE_TYPE);
                postmanServiceInfo.setHost(hostAddress);
                postmanServiceInfo.setPort(port);

                Logcat.v(TAG, "Broadcasting %s", postmanServiceInfo.toString());

                try {
                    broadcastLatch = new CountDownLatch(1);
                    nsdManager.registerService(
                            postmanServiceInfo, NsdManager.PROTOCOL_DNS_SD, serviceRegistrationListener);
                    broadcastLatch.await();
                } catch (Throwable e) {
                    subscriber.onError(e);

                } finally {
                    broadcastActive.set(false);
                }

            } else {
                Logcat.w(TAG, "Already broadcasting service");
                subscriber.onNext(PostmanBroadcastEvent.alreadyBroadcasting());
                subscriber.onComplete();
            }
        }).subscribeOn(io);


    }


    @Override
    public void stopServiceBroadcast() {
        Log.d(TAG, "Stopping Service Broadcast");
        broadcastLatch.countDown();
        try {
            nsdManager.unregisterService(serviceRegistrationListener);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Problem when stopping service broadcasting", e);
        }
    }

    private class ServiceRegistrationListener implements RegistrationListener {
        private final Subscriber<? super PostmanBroadcastEvent> subscriber;

        ServiceRegistrationListener(Subscriber<? super PostmanBroadcastEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service registration failed (%s)", translateErrorCode(errorCode));
            subscriber.onError(new PostmanBroadcastException(translateErrorCode(errorCode)));
            stopServiceBroadcast();

        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service deregistration failed (%d)", errorCode);
            broadcastLatch.countDown();
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service registration succeeded");
            subscriber.onNext(PostmanBroadcastEvent.broadcastStarted());
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Logcat.d(TAG, "Siia Service unregistered");
            if(broadcastActive.get()) {
                subscriber.onNext(PostmanBroadcastEvent.broadcastStopped());
                subscriber.onComplete();
                stopServiceBroadcast();
            }
        }

    }


    @Override
    public Flowable<PostmanDiscoveryEvent> discoverService(@NonNull String serviceName, @NonNull InetAddress addressToSearchOn) {
        return Flowable.<PostmanDiscoveryEvent>fromPublisher(
                subscriber -> {

                    if (discoveryActive.compareAndSet(false, true)) {
                        Logcat.d(TAG, "Searching for service %s", serviceName);
                        serviceDiscoveryListener = new ServiceDiscoveryListener(subscriber);

                        try {
                            discoveryLatch = new CountDownLatch(1);
                            nsdManager.discoverServices(
                                    Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, serviceDiscoveryListener);
                            discoveryLatch.await();
                        } catch (Exception e) {
                            subscriber.onError(e);
                        } finally {
                            discoveryActive.set(false);
                        }

                    } else {

                        Logcat.w(TAG, "Already discovering");
                        subscriber.onNext(PostmanDiscoveryEvent.alreadyDiscovering());
                        subscriber.onComplete();
                    }

                })
                .subscribeOn(io);

    }

    @Override
    public void stopDiscovery() {
        Logcat.i(TAG, "Stopping Siia Service Discovery");
        discoveryLatch.countDown();
        try {
            nsdManager.stopServiceDiscovery(serviceDiscoveryListener);
        } catch (Exception error) {
            Log.w(TAG, "Error when stopping discovery", error);
        }
    }



    private class ServiceDiscoveryListener implements NsdManager.DiscoveryListener {


        private final Subscriber<? super PostmanDiscoveryEvent> subscriber;

        ServiceDiscoveryListener(Subscriber<? super PostmanDiscoveryEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Logcat.w(TAG, "Service discovery (start) failed (%d)", errorCode);
            subscriber.onError(new PostmanBroadcastException(translateErrorCode(errorCode)));
            stopDiscovery();
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Logcat.w(TAG, "Stop Service discovery failed (%d)", errorCode);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Logcat.d(TAG, "Started discovery for %s", serviceType);
            subscriber.onNext(PostmanDiscoveryEvent.started());
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Logcat.d(TAG, "Stopped discovery for %s", serviceType);
            if(discoveryActive.get()) {
                subscriber.onNext(PostmanDiscoveryEvent.discoveryStopped());
                subscriber.onComplete();
                stopDiscovery();
            }
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            Logcat.d(TAG, "Siia Service Found");
            try {
                nsdManager.resolveService(serviceInfo, new ServiceResolutionHandler(subscriber));
            } catch(Exception e) {
                subscriber.onError(e);
                stopDiscovery();
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Logcat.w(TAG, "Service Lost");
            subscriber.onNext(PostmanDiscoveryEvent.lost());
        }


    }

    //Service Resolution
    private static class ServiceResolutionHandler implements NsdManager.ResolveListener {

        private final Subscriber<? super PostmanDiscoveryEvent> subscriber;

        ServiceResolutionHandler(Subscriber<? super PostmanDiscoveryEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service Resolution Failed (%d)", errorCode);
        }

        @SuppressLint("VisibleForTests")
        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Service Resolution Succeeded");
            Logcat.d(TAG, "Service Port : " + serviceInfo.getPort());
            Logcat.d(TAG, "Service Port : " + serviceInfo.getServiceName());
            Logcat.d(TAG, "Service Host Address : " + serviceInfo.getHost().getHostAddress());
            Logcat.d(TAG, "Service Host Name : " + serviceInfo.getHost().getHostName());
            Logcat.d(TAG, "Service Attributes : " + serviceInfo.getAttributes());
            subscriber.onNext(new PostmanDiscoveryEvent(new InetAddressServiceDetails(serviceInfo.getHost(), serviceInfo.getPort())));

        }

    }



    private String translateErrorCode(int code) {
        switch (code) {
            case NsdManager.FAILURE_INTERNAL_ERROR:
                return "Internal NSD Error";
            case NsdManager.FAILURE_ALREADY_ACTIVE:
                return "Already broadcasting";
            case NsdManager.FAILURE_MAX_LIMIT:
                return "Max NSD service limit";
            default:
                return "Unknown";
        }

    }

}
