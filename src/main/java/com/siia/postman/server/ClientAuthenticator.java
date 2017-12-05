package com.siia.postman.server;

import com.siia.commons.core.log.Logcat;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;

import static com.siia.postman.server.Connection.*;

public class ClientAuthenticator {

    private static final String TAG = Logcat.getTag();
    private PostmanClient client;
    private Disposable disposable;
    private State state;

    private enum State {
        NOT_STARTED,
        INPROGRESS,
        AUTHENTICATED,
        AUTH_FAILED

    }

    public ClientAuthenticator(PostmanClient client) {
        this.client = client;
        state = State.NOT_STARTED;
    }

    public ReplaySubject<State> beginAuthentication(Observable<PostmanClientEvent> eventStream, PostmanMessage initialMsg) {
        ReplaySubject<State> events = ReplaySubject.create();

        disposable = eventStream
                .observeOn(Schedulers.computation())
                .filter(PostmanClientEvent::isNewMessage)
                .map(PostmanClientEvent::msg)
                .startWith(initialMsg)
                .subscribe(message -> {

                            switch (state) {
                                case NOT_STARTED:
                                    state = State.INPROGRESS;
                                    events.onNext(state);
                                    Auth.AuthChallenge challenge = Auth.AuthChallenge.parseFrom(message.getBody().array());
                                    Logcat.d(TAG, logMsg("Auth Challenge received ", client.getClientId()));
                                    Logcat.v(TAG, logMsg(challenge.toString(), client.getClientId()));
                                    Auth.AuthToken authToken = Auth.AuthToken.newBuilder().setToken("token").setIdentity(client.getClientId().toString()).build();
                                    Auth.AuthResponse authResponse = Auth.AuthResponse.newBuilder().setParticipantId(client.getClientId().toString())
                                            .setAuthToken(authToken).build();
                                    client.sendMessage(new PostmanMessage(authResponse.toByteArray()));
                                    break;
                                case INPROGRESS:
                                    ResponseOuterClass.Response response = ResponseOuterClass.Response.parseFrom(message.getBody().array());
                                    if (response.getOk()) {
                                        Logcat.d(TAG, logMsg("Authenticated", client.getClientId()));
                                        state = State.AUTHENTICATED;
                                        events.onNext(state);
                                    } else {
                                        Logcat.d(TAG, logMsg("Auth Failed", client.getClientId()));
                                        state = State.AUTH_FAILED;
                                        events.onNext(state);
                                    }
                                    break;
                                default:
                                    throw new AuthenticationException("Auth Protocol Failure : "+ message);

                            }

                        },
                        error -> {
                            Logcat.e(TAG, logMsg("Problem authenticating client", client.getClientId()), error);
                            events.onNext(State.AUTH_FAILED);
                            events.onComplete();
                        });

        return events;
    }

    public void cancelTask() {
        disposable.dispose();
    }

    class AuthenticationException extends RuntimeException {
        AuthenticationException(String message) {
            super(message);
        }
    }

}
