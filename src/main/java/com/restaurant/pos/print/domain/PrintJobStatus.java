package com.restaurant.pos.print.domain;

public enum PrintJobStatus {
    PENDING,
    CLAIMED,
    LEASED,
    LOCAL_QUEUED,
    SPOOLING,
    SPOOLED,
    COMPLETED,
    PRINTED,
    FAILED,
    RETRY,
    RETRY_WAIT,
    HELD_AMBIGUOUS
}
