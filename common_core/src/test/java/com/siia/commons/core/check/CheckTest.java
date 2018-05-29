package com.siia.commons.core.check;

import org.junit.Test;

public class CheckTest {

    @Test
    public void shouldNotThrowExceptionIfStateIsTrue() {
        Check.checkState(true, "");
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfStateIsFalse() {
        Check.checkState(false, "");
    }
}