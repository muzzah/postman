package com.siia.postman.discovery;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.osiyent.sia.commons.core.log.Logcat;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AndroidNdsDiscoveryService implements PostmanDiscoveryService {
    private static final String SERVICE_TYPE = "_siia._tcp.";
    private static final String TAG = Logcat.getTag();

    private final NsdManager nsdManager;
    private final NsdManager.RegistrationListener serviceRegistrationListener;
    private final NsdManager.DiscoveryListener discoveryListener;
    private final ServiceResolveListener resolveListener;
    private final AtomicBoolean active;


    @Inject
    AndroidNdsDiscoveryService(NsdManager ndsManager) {
        this.nsdManager = ndsManager;
        this.serviceRegistrationListener = new ServiceRegistrationListener();
        this.discoveryListener = new ServiceDiscoveryListener();
        this.resolveListener = new ServiceResolveListener();
        this.active = new AtomicBoolean(false);
    }

    @Override
    public void startServiceBroadcast(int port, String hostAddress) {
//        checkState(isRunning(), "Start server before starting announcements");

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


    @Override
    public void stopServiceBroadcast() {
        if (!isBroadcasting()){
            return;
        }
        Log.d(TAG, "Stopping Service Broadcast");
        try {
            nsdManager.unregisterService(serviceRegistrationListener);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Problem when stopping service broadcasting", e);
        }
    }


    private boolean isBroadcasting() {
        return active.get();
    }


    @Override
    public void startServiceDiscovery() {
        nsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);

    }

    @Override
    public void stopServiceDiscovery() {

        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Problem when stopping service discovery", e);
        }
    }


    private class ServiceDiscoveryListener implements NsdManager.DiscoveryListener {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Logcat.w(TAG, "Service discovery (start) failed (%d)", errorCode);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Logcat.w(TAG, "Service discovery (stop) failed (%d)", errorCode);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Logcat.i(TAG, "Started discovery for %s", serviceType);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Logcat.i(TAG, "Stopped discovery for %s", serviceType);
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Service %s Found  ", SERVICE_TYPE);
            nsdManager.resolveService(serviceInfo, resolveListener);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Logcat.w(TAG, "Service Lost" );
        }
    }

    private class ServiceResolveListener implements NsdManager.ResolveListener {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service Resolution Failed (%d)", errorCode);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Service Resolution Succeeded");
            Logcat.d(TAG, "Service Port : " + serviceInfo.getPort());
            Logcat.d(TAG, "Service Port : " + serviceInfo.getServiceName());
            Logcat.d(TAG, "Service Host Address : " + serviceInfo.getHost().getHostAddress());
            Logcat.d(TAG, "Service Host Name : " + serviceInfo.getHost().getHostName());
            Logcat.d(TAG, "Service Attributes : " + serviceInfo.getAttributes());

        }
    }

    private class ServiceRegistrationListener implements NsdManager.RegistrationListener {
        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service registration failed (%d)", errorCode);
            active.getAndSet(false);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service unregistration failed (%d)", errorCode);
            active.getAndSet(false);
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service registration succeeded");
            active.getAndSet(true);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service unregistered");
            active.getAndSet(false);
        }
    }

}
