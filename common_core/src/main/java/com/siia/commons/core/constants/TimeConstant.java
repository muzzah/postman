package com.siia.commons.core.constants;

import java.util.concurrent.TimeUnit;

/**
 * Copyright Siia 2018
 */

public enum TimeConstant {
    NETWORK_LATCH_TIME_WAIT(TimeUnit.SECONDS, 10L),
    RACE_CONDITION_WAIT_2S(TimeUnit.SECONDS, 2L),
    RETRY_DELAY(TimeUnit.MILLISECONDS, 500L);

    private final TimeUnit timeUnit;
    private final Long constantValue;

    TimeConstant(TimeUnit timeUnit, Long constantValue) {
        this.timeUnit = timeUnit;
        this.constantValue = constantValue;
    }

    @SuppressWarnings("unchecked")
    public Long val() {
        return constantValue;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
