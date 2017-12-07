package com.siia.postman.server;

import com.siia.commons.core.log.Logcat;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.siia.postman.server.Connection.logMsg;

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
                                Auth.AuthChallenge challenge = Auth.AuthChallenge.parseFrom(message.getBody().array());
                                Logcat.d(TAG, logMsg("Auth Challenge received ", client.getClientId()));
                                Logcat.v(TAG, logMsg(challenge.toString(), client.getClientId()));
                                Auth.AuthToken authToken = Auth.AuthToken.newBuilder().setToken("token").setIdentity(client.getClientId().toString()).build();
                                Auth.AuthResponse authResponse = Auth.AuthResponse.newBuilder().setParticipantId(client.getClientId().toString())
                                        .setAuthToken(authToken).build();
                                client.sendMessage(new PostmanMessage(authResponse.toByteArray()));
                            } else if (state.get() == State.INPROGRESS) {
                                ResponseOuterClass.Response response = ResponseOuterClass.Response.parseFrom(message.getBody().array());
                                if (response.getOk()) {
                                    Logcat.d(TAG, logMsg("Authenticated", client.getClientId()));
                                    state.set(State.AUTHENTICATED);
                                } else {
                                    Logcat.d(TAG, logMsg("Auth Failed", client.getClientId()));
                                    state.set(State.AUTH_FAILED);
                                }
                                disposable.dispose();
                            }

                        },
                        error -> {
                            Logcat.e(TAG, logMsg("Problem authenticating connection", client.getClientId()), error);
                            state.set(State.AUTH_FAILED);
                        });

    }

    public void cancelTask() {
        disposable.dispose();
    }

    public boolean isAuthenticated() {
        return State.AUTHENTICATED.equals(state);
    }

}
