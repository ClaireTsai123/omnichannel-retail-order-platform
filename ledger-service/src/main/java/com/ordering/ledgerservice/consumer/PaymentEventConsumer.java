package com.ordering.ledgerservice.consumer;

import com.ordering.common.domain.LedgerTransactionType;
import com.ordering.common.dto.LedgerEntryRequest;
import com.ordering.common.event.PaymentEvent;
import com.ordering.common.event.PaymentEventType;
import com.ordering.common.kafka.KafkaTopics;
import com.ordering.ledgerservice.service.LedgerEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LedgerEntryService ledgerService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS_TOPIC, groupId = "ledger-service-group")
    public void consume(PaymentEvent event) {
        if (event.getEventType() == PaymentEventType.PAYMENT_AUTHORIZED) {
            createAuthorizationLedgerEntry(event);
        }

        if (event.getEventType() == PaymentEventType.PAYMENT_REFUNDED) {
            createRefundLedgerEntry(event);
        }
    }

    private void createAuthorizationLedgerEntry(PaymentEvent event) {
        LedgerEntryRequest request = new LedgerEntryRequest();
        request.setTransactionId("AUTH-" + event.getPaymentId());
        request.setPaymentId(event.getPaymentId());
        request.setOrderId(event.getOrderId());
        request.setUserId(event.getUserId());
        request.setAmount(event.getAmount());
        request.setTransactionType(LedgerTransactionType.AUTHORIZATION);

        ledgerService.createEntry(request);
    }

    private void createRefundLedgerEntry(PaymentEvent event) {
        LedgerEntryRequest request = new LedgerEntryRequest();
        request.setTransactionId("REFUND-" + event.getPaymentId());
        request.setPaymentId(event.getPaymentId());
        request.setOrderId(event.getOrderId());
        request.setUserId(event.getUserId());
        request.setAmount(event.getAmount().negate());
        request.setTransactionType(LedgerTransactionType.REFUND);

        ledgerService.createEntry(request);
    }
}
