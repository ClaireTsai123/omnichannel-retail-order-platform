package com.ordering.orderservice.consumer;

import com.ordering.common.event.PaymentEvent;
import com.ordering.common.event.PaymentEventType;
import com.ordering.orderservice.entity.ProcessedKafkaEvent;
import com.ordering.orderservice.repository.ProcessedKafkaEventRepository;
import com.ordering.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventConsumerTest {

    @Test
    void duplicatePaymentFailedEventDoesNotInvokeOrderServiceAgain() {
        Set<String> processedEvents = new HashSet<>();
        processedEvents.add("payment-event-1");
        AtomicInteger failureCalls = new AtomicInteger();
        PaymentEventConsumer consumer = new PaymentEventConsumer(
                orderServiceCountingFailures(failureCalls),
                processedEventRepository(processedEvents)
        );

        consumer.consumePaymentEvent(paymentFailedEvent("payment-event-1"));

        assertThat(failureCalls).hasValue(0);
        assertThat(processedEvents).containsExactly("payment-event-1");
    }

    @Test
    void newPaymentFailedEventInvokesOrderServiceAndMarksProcessed() {
        Set<String> processedEvents = new HashSet<>();
        AtomicInteger failureCalls = new AtomicInteger();
        PaymentEventConsumer consumer = new PaymentEventConsumer(
                orderServiceCountingFailures(failureCalls),
                processedEventRepository(processedEvents)
        );

        consumer.consumePaymentEvent(paymentFailedEvent("payment-event-2"));

        assertThat(failureCalls).hasValue(1);
        assertThat(processedEvents).contains("payment-event-2");
    }

    private PaymentEvent paymentFailedEvent(String eventId) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(eventId);
        event.setEventType(PaymentEventType.PAYMENT_FAILED);
        event.setPaymentId(2001L);
        event.setOrderId(1001L);
        return event;
    }

    private OrderService orderServiceCountingFailures(AtomicInteger failureCalls) {
        return (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("handlePaymentFailed")
                            && args.length == 3) {
                        failureCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "OrderService test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private ProcessedKafkaEventRepository processedEventRepository(Set<String> processedEvents) {
        return (ProcessedKafkaEventRepository) Proxy.newProxyInstance(
                ProcessedKafkaEventRepository.class.getClassLoader(),
                new Class<?>[]{ProcessedKafkaEventRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsByEventId")) {
                        return processedEvents.contains((String) args[0]);
                    }
                    if (method.getName().equals("save")) {
                        processedEvents.add(((ProcessedKafkaEvent) args[0]).getEventId());
                        return args[0];
                    }
                    if (method.getName().equals("toString")) {
                        return "ProcessedKafkaEventRepository test proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
