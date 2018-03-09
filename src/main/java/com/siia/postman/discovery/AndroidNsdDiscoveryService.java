package com.siia.postman.discovery;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.siia.commons.core.log.Logcat;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;

public class AndroidNsdDiscoveryService implements PostmanDiscoveryService {
    private static final String SERVICE_TYPE = "_siia._tcp.";
    private static final String TAG = Logcat.getTag();

    private final NsdManager nsdManager;
    private final NsdManager.RegistrationListener serviceRegistrationListener;
    private final AtomicBoolean broadcastActive;
    private PublishSubject<PostmanDiscoveryEvent> discoverEventsStream;
    private ServiceDiscoveryListener listener;


    public AndroidNsdDiscoveryService(NsdManager ndsManager) {
        this.nsdManager = ndsManager;
        this.serviceRegistrationListener = new ServiceRegistrationListener();
        this.broadcastActive = new AtomicBoolean(false);
    }

    @Override
    public void startServiceBroadcast(int port, String hostAddress) {
        checkState(!isBroadcasting(), "Already broadcasting service");
        if (broadcastActive.compareAndSet(false, true)) {
            NsdServiceInfo postmanServiceInfo = new NsdServiceInfo();

            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            postmanServiceInfo.setServiceName("Siia_T_TEACHERID");
            postmanServiceInfo.setServiceType(SERVICE_TYPE);
            postmanServiceInfo.setAttribute("tname", "Sanne De Vries");
            try {
                postmanServiceInfo.setHost(Inet4Address.getByName(hostAddress));
            } catch (UnknownHostException e) {
                Logcat.e(TAG, "Cannot start service discovery as host address seems incorrect", e);
                return;
            }
            postmanServiceInfo.setPort(port);

            nsdManager.registerService(
                    postmanServiceInfo, NsdManager.PROTOCOL_DNS_SD, serviceRegistrationListener);
        }
    }


    @Override
    public void stopServiceBroadcast() {
        checkState(isBroadcasting(), "Not broadcasting service");
        Log.d(TAG, "Stopping Service Broadcast");
        try {
            nsdManager.unregisterService(serviceRegistrationListener);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Problem when stopping service broadcasting", e);
        }
    }

    @Override
    public PublishSubject<PostmanDiscoveryEvent> getDiscoveryEventStream() {
        discoverEventsStream = PublishSubject.create();
        return discoverEventsStream;
    }


    @Override
    public boolean isBroadcasting() {
        return broadcastActive.get();
    }


    @Override
    public void discoverService() {
        Completable.fromPublisher(
                emitter -> {
                    listener = new ServiceDiscoveryListener(nsdManager, discoverEventsStream);
                    nsdManager.discoverServices(
                            SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);

                    emitter.onComplete();


                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(
                        () -> {
                        },
                        error -> discoverEventsStream.onError(error)
                );

    }

    @Override
    public void stopDiscovery() {
        Logcat.i(TAG, "Stopping Siia Service Discovery");
        try {
            nsdManager.stopServiceDiscovery(listener);
        } catch (Exception error){
            Log.w(TAG, "Error when stopping discovery", error);
        }
    }


    private static class ServiceDiscoveryListener implements NsdManager.DiscoveryListener {


        private NsdManager nsdManager;
        private PublishSubject<PostmanDiscoveryEvent> eventStream;

        ServiceDiscoveryListener(NsdManager nsdManager, PublishSubject<PostmanDiscoveryEvent> eventStream) {
            this.nsdManager = nsdManager;
            this.eventStream = eventStream;
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Logcat.w(TAG, "Service discovery (start) failed (%d)", errorCode);
            eventStream.onNext(PostmanDiscoveryEvent.notFound());
            eventStream.onComplete();
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Logcat.w(TAG, "Service discovery failed (%d)", errorCode);
            eventStream.onNext(PostmanDiscoveryEvent.notFound());
            eventStream.onComplete();
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Logcat.i(TAG, "Started discovery for %s", serviceType);
            eventStream.onNext(PostmanDiscoveryEvent.started());
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Logcat.i(TAG, "Stopped discovery for %s", serviceType);
            eventStream.onComplete();
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service Found");
            nsdManager.resolveService(serviceInfo, new ServiceResolutionHandler(eventStream));
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Logcat.w(TAG, "Service Lost");
            eventStream.onNext(PostmanDiscoveryEvent.notFound());
        }


    }

    //Service Resolution
    private static class ServiceResolutionHandler implements NsdManager.ResolveListener {


        private final PublishSubject<PostmanDiscoveryEvent> eventStream;

        ServiceResolutionHandler(PublishSubject<PostmanDiscoveryEvent> eventStream) {
            this.eventStream = eventStream;
        }

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service Resolution Failed (%d)", errorCode);
            eventStream.onNext(PostmanDiscoveryEvent.notFound());
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Service Resolution Succeeded");
            Logcat.d(TAG, "Service Port : " + serviceInfo.getPort());
            Logcat.d(TAG, "Service Port : " + serviceInfo.getServiceName());
            Logcat.d(TAG, "Service Host Address : " + serviceInfo.getHost().getHostAddress());
            Logcat.d(TAG, "Service Host Name : " + serviceInfo.getHost().getHostName());
            Logcat.d(TAG, "Service Attributes : " + serviceInfo.getAttributes());
            eventStream.onNext(PostmanDiscoveryEvent.found(serviceInfo.getHost(), serviceInfo.getPort()));

        }

    }


    private class ServiceRegistrationListener implements NsdManager.RegistrationListener {
        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service registration failed (%d)", errorCode);
            broadcastActive.getAndSet(false);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service deregistration failed (%d)", errorCode);
            broadcastActive.getAndSet(false);
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service registration succeeded");
            broadcastActive.getAndSet(true);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service unregistered");
            broadcastActive.getAndSet(false);
        }
    }

}
