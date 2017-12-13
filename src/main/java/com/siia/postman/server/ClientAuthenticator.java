package com.siia.postman.server;

import com.siia.commons.core.log.Logcat;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ClientAuthenticator {

    private static final String TAG = Logcat.getTag();
    private PostmanClient client;
    private Disposable disposable;
    private AtomicReference<State> state;


    private enum State {
        NOT_STARTED,
        INPROGRESS,
        AUTHENTICATED,
        AUTH_FAILED

    }

    public ClientAuthenticator(PostmanClient client) {
        this.client = client;
        state = new AtomicReference<>(State.NOT_STARTED);
    }

    public void beginAuthentication(Observable<PostmanClientEvent> eventStream, PostmanMessage initialMsg) {

        disposable = eventStream
                .observeOn(Schedulers.computation())
                .filter(PostmanClientEvent::isNewMessage)
                .map(PostmanClientEvent::msg)
                .startWith(initialMsg)
                .subscribe(message -> {
                            if (state.compareAndSet(State.NOT_STARTED, State.INPROGRESS)) {
                                Auth.AuthChallenge challenge = message.getProtoObj();
                                Auth.AuthToken authToken = Auth.AuthToken.newBuilder().setToken("token").setIdentity(client.getClientId().toString()).build();
                                Auth.AuthResponse authResponse = Auth.AuthResponse.newBuilder().setParticipantId(client.getClientId().toString())
                                        .setAuthToken(authToken).build();
                                client.sendMessage(new PostmanMessage(authResponse));
                            } else if (state.get() == State.INPROGRESS) {
                                MessageOuterClass.Response response = message.getProtoObj();
                                if (response.getOk()) {
                                    state.set(State.AUTHENTICATED);
                                } else {
                                    state.set(State.AUTH_FAILED);
                                }
                                disposable.dispose();
                            }

                        },
                        error -> {
                            Logcat.e(TAG, "Problem authenticating connection", error);
                            state.set(State.AUTH_FAILED);
                        });

    }

    public void cancelTask() {
        disposable.dispose();
    }

    public boolean isAuthenticated() {
        return State.AUTHENTICATED.equals(state.get());
    }

}
