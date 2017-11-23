package com.siia.postman.server;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdManager.RegistrationListener;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.osiyent.sia.commons.core.log.Logcat;

import java.net.InetSocketAddress;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;

import static io.reactivex.schedulers.Schedulers.newThread;


public class IPPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();
    private static final String SERVICE_TYPE = "_siia._tcp.";
    private final InetSocketAddress bindAddress;
    private Flowable<NetworkEvent> serverEventLoop;
    private Disposable serverEventLoopDisposable;
    private MainNIOEventLoop mainNioEventLoop;
    private final Context context;
    private final NsdManager nsdManager;
    private final RegistrationListener serviceRegistrationListener;
    private final DiscoveryListener discoveryListener;
    private final ServiceResolveListener resolveListener;

    @Inject
    public IPPostmanServer(Context context, NsdManager nsdManager) {
        this.context = context;
        this.nsdManager = nsdManager;
        this.resolveListener = new ServiceResolveListener();
        this.bindAddress = new InetSocketAddress("0.0.0.0", 0);
        this.serviceRegistrationListener = new ServiceRegistrationListener();
        this.discoveryListener = new ServiceDiscoveryListener();
    }


    @Override
    public void startServer(final NetworkEventListener networkEventListener) {
        if (serverEventLoop != null) {
            Logcat.w(TAG, "PostmanServer already started");
            return;
        }
        mainNioEventLoop = new MainNIOEventLoop(bindAddress);
        serverEventLoop = Flowable.fromPublisher(mainNioEventLoop)
                .subscribeOn(newThread())
                .observeOn(newThread());

        serverEventLoopDisposable = serverEventLoop.subscribe(

                (event) -> {
                    switch (event.type()) {
                        case CLIENT_JOIN:
                            networkEventListener.onClientJoin(event.clientId());
                            break;
                        case NEW_DATA:
                            networkEventListener.onClientData(event.data(), event.clientId());
                            break;
                        case CLIENT_DISCONNECT:
                            networkEventListener.onClientDisconnect(event.clientId());
                            break;
                        case SERVER_LISTENING:
                            networkEventListener.onServerListening();
                            startServerServiceBroadcasting(event.getListeningPort());
                            break;
                        default:
                            Logcat.w(TAG, "Unrecognised Network Event : %s", event.type());
                    }
                },
                (error) -> {
                    Logcat.e(TAG, "Abnormal PostmanServer Shutdown", error);
                    stopServerServiceBroadcasting();
                },
                () -> {
                    Logcat.i(TAG, "PostmanServer Stopped");
                    stopServerServiceBroadcasting();
                }
        );

    }

    @Override
    public void stopServer() {
        if (mainNioEventLoop != null && mainNioEventLoop.isRunning()) {
            mainNioEventLoop.shutdownLoop();
        }
        //If we dispose we might not fire off the closing event
//        if (serverEventLoop != null && !serverEventLoopDisposable.isDisposed()) {
//            serverEventLoopDisposable.dispose();
//        }
        serverEventLoop = null;
    }

    @Override
    public boolean isRunning() {
        return mainNioEventLoop != null && mainNioEventLoop.isRunning() && serverEventLoop != null && !serverEventLoopDisposable.isDisposed();
    }

    @Override
    public void getClient(int clientId) {

    }

    private void startServerServiceBroadcasting(int listeningPort) {
//        checkState(isRunning(), "Start server before starting announcements");

        NsdServiceInfo postmanServiceInfo  = new NsdServiceInfo();

        // The name is subject to change based on conflicts
        // with other services advertised on the same network.
        postmanServiceInfo.setServiceName("Siia_T_TEACHERID");
        postmanServiceInfo.setServiceType(SERVICE_TYPE);
        postmanServiceInfo.setAttribute("tname", "Sanne De Vries");
        postmanServiceInfo.setHost(bindAddress.getAddress());
        postmanServiceInfo.setPort(listeningPort);

        nsdManager.registerService(
                postmanServiceInfo, NsdManager.PROTOCOL_DNS_SD, serviceRegistrationListener);
    }

    private void stopServerServiceBroadcasting() {
        try {
            nsdManager.unregisterService(serviceRegistrationListener);
        } catch(IllegalArgumentException e ){
            Log.w(TAG, "Problem when stopping service broadcasting", e);
        }
    }


    @Override
    public void discoverServerService() {
        nsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);

    }

    @Override
    public void cancelDiscovery() {

        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
        } catch(IllegalArgumentException e ){
            Log.w(TAG, "Problem when stopping service discovery", e);
        }
    }


    private class ServiceDiscoveryListener implements DiscoveryListener {
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
            Logcat.w(TAG, "Service Lost : " + serviceInfo.getAttributes());
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
    private class ServiceRegistrationListener implements RegistrationListener {
        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service registration failed (%d)", errorCode);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Logcat.e(TAG, "Siia Service unregistration failed (%d)", errorCode);
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service registration succeeded");
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Logcat.i(TAG, "Siia Service unregistered");
        }
    }


}
