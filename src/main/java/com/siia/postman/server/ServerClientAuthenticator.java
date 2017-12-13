package com.siia.postman.server;

import com.siia.commons.core.log.Logcat;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;

public class ServerClientAuthenticator {

    private static final String TAG = Logcat.getTag();
    private PostmanServer postmanServer;
    private Observable<ServerEvent> serverEventsStream;
    private final Connection client;
    private Disposable disposable;

    public enum State {
        INPROGRESS,
        AUTHENTICATED,
        AUTH_FAILED

    }

    public ServerClientAuthenticator(PostmanServer postmanServer,Observable<ServerEvent> serverEventsStream, Connection client) {
        this.postmanServer = postmanServer;
        this.serverEventsStream = serverEventsStream;
        this.client = client;
    }

    public ReplaySubject<State> beginAuthentication() {
        ReplaySubject<State> events = ReplaySubject.create();

        disposable = serverEventsStream
                .observeOn(Schedulers.computation())
                .filter(event -> event.isNewMessageFor(client))
                .doOnSubscribe(disposable -> {
                    events.onNext(State.INPROGRESS);
                    Auth.AuthChallenge challenge = Auth.AuthChallenge.newBuilder().setHostId(postmanServer.getId())
                            .build();
                    postmanServer.sendMessage(new PostmanMessage(challenge), client);

                })
                .subscribe(clientEvent -> {
                            PostmanMessage msg = clientEvent.message();
                            Auth.AuthResponse response = msg.getProtoObj();

                            MessageOuterClass.Response ok = MessageOuterClass.Response.newBuilder().setOk(true).build();
                            postmanServer.sendMessage(new PostmanMessage(ok), client);

                            events.onNext(State.AUTHENTICATED);
                            events.onComplete();
                            disposable.dispose();


                        },
                        error -> {
                            Logcat.e(TAG, client.getConnectionId(),"Problem authenticating connection", error);
                            events.onNext(State.AUTH_FAILED);
                            events.onComplete();
                            disposable.dispose();
                        });

        return events;
    }

    public void cancelTask() {
        disposable.dispose();
    }




}
