package com.eventflow.entity;

public enum DeliveryStatus {
    PENDING,
    DELIVERING,
    DELIVERED,
    RETRY_SCHEDULED,
    DEAD_LETTER
}
