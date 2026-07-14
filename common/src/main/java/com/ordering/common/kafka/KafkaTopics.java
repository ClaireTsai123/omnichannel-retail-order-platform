package com.ordering.common.kafka;

public final class KafkaTopics {
    private KafkaTopics() {
        // Prevent instantiation
    }
    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    public static final String PAYMENT_EVENTS_DLQ_TOPIC = "payment-events-dlq";
    public static final String ORDER_EVENTS_TOPIC = "order-events";
    public static final String FULFILLMENT_EVENTS_TOPIC = "fulfillment-events";
    public static final String FULFILLMENT_EVENTS_DLQ_TOPIC = "fulfillment-events-dlq";
    public static final String LEDGER_PAYMENT_EVENTS_DLQ_TOPIC =
            "ledger-payment-events-dlq";
    public static final String FULFILLMENT_ORDER_EVENTS_DLQ_TOPIC =
            "fulfillment-order-events-dlq";
    public static final String LEDGER_EVENTS_TOPIC = "ledger-events";
    public static final String LEDGER_EVENTS_DLQ_TOPIC = "ledger-events-dlq";
    public static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

}
