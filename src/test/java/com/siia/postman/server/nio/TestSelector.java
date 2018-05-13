package com.siia.postman.server.nio;

import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;

import org.mockito.internal.util.collections.Sets;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class TestSelector extends AbstractSelector {
    public boolean closeAfterRegistration = false;
    private Queue<Set<SelectionKey>> selectedKeysToReturnInOrder = new ArrayDeque<>();
    public boolean throwAndCloseAfterRegistration = false;
    public boolean closeOnThirdSelect = false;
    public boolean addMessageOnSecondSelect = false;
    public int registrationCount = 0;
    public int expectedRegistration = 0;
    public int selectCount = 0;
    public int wakeupCount = 0;
    public boolean shutDownAfterSecondSelect;

    public Map<AbstractSelectableChannel, Integer> registrationOps = new HashMap<>();
    private MessageQueueLoop messageQueueLoop;
    private PostmanMessage postmanMessage;
    private Connection client;
    private SelectionKey selectionKey;
    private ServerEventLoop serverEventLoop;
    public boolean closed;
    private Set<SelectionKey> keysToReturn;

    protected TestSelector(SelectorProvider provider, MessageQueueLoop messageQueueLoop, PostmanMessage postmanMessage, Connection client,
                           SelectionKey selectionKey) {
        super(provider);
        this.messageQueueLoop = messageQueueLoop;
        this.postmanMessage = postmanMessage;
        this.client = client;
        this.selectionKey = selectionKey;
    }

    public TestSelector(SelectorProvider selectorProvider, PostmanMessage postmanMessage, Connection client, SelectionKey selectionKey, ServerEventLoop serverEventLoop) {
        super(selectorProvider);
        this.postmanMessage = postmanMessage;
        this.client = client;
        this.selectionKey = selectionKey;
        this.serverEventLoop = serverEventLoop;
    }


    @Override
    protected void implCloseSelector() {
        closed = true;
    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        registrationCount++;
        registrationOps.put(ch, ops);

        if(closeAfterRegistration && expectedRegistration == registrationCount) {
            closeQuietly();
        } else if(throwAndCloseAfterRegistration) {
            closeQuietly();
            throw new RuntimeException();
        }
        return selectionKey;
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            //Ignore
        }
    }

    @Override
    public Set<SelectionKey> keys() {
        return null;
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return keysToReturn;
    }

    @Override
    public int selectNow() {
        return 0;
    }

    @Override
    public int select(long timeout) {
        return 0;
    }

    @Override
    public int select() throws IOException {
        selectCount++;

        if(addMessageOnSecondSelect && selectCount == 2) {
            if(messageQueueLoop != null) {
                messageQueueLoop.addMessageToQueue(postmanMessage, client);
            }

            if(serverEventLoop != null) {
                serverEventLoop.addMessageToQueue(postmanMessage, client);
            }
        }

        if(closeOnThirdSelect && selectCount == 3) {
            close();
        }

        if(shutDownAfterSecondSelect && selectCount == 2) {
            messageQueueLoop.shutdown();
        }

        keysToReturn = selectedKeysToReturnInOrder.peek() == null ? Collections.emptySet() : selectedKeysToReturnInOrder.poll();
        return keysToReturn.size();
    }

    @Override
    public Selector wakeup() {
        wakeupCount++;
        return this;
    }

    public void addSelectionKeyToReturn(SelectionKey... selectionKey) {
        Set<SelectionKey> keys = selectionKey == null ? Collections.emptySet() : Sets.newSet(selectionKey);
        selectedKeysToReturnInOrder.offer(keys);
    }

}