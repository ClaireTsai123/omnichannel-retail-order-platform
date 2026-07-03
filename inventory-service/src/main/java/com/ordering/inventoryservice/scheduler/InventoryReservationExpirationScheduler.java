package com.ordering.inventoryservice.scheduler;

import com.ordering.inventoryservice.entity.Inventory;
import com.ordering.inventoryservice.entity.InventoryReservation;
import com.ordering.inventoryservice.entity.ReservationStatus;
import com.ordering.inventoryservice.metrics.InventoryMetrics;
import com.ordering.inventoryservice.repository.InventoryRepository;
import com.ordering.inventoryservice.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservationExpirationScheduler {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryMetrics inventoryMetrics;

    @Value("${inventory.reservation.expiration-minutes:30}")
    private long reservationExpirationMinutes;

    @Scheduled(fixedDelayString = "${inventory.reservation.scheduler-interval-ms:300000}")
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(reservationExpirationMinutes);
        List<InventoryReservation> expiredReservations =
                reservationRepository.findByStatusAndReservedAtBefore(ReservationStatus.RESERVED, cutoffTime);

        for (InventoryReservation reservation : expiredReservations) {
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            Inventory inventory = inventoryRepository.findBySku(reservation.getSku())
                    .orElseThrow(() -> new RuntimeException("Inventory not found for sku: " + reservation.getSku()));

            if (inventory.getReservedQuantity() < reservation.getQuantity()) {
                throw new RuntimeException("Reserved quantity is inconsistent for sku: " + reservation.getSku());
            }

            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + reservation.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
            reservation.setStatus(ReservationStatus.RELEASED);

            inventoryRepository.save(inventory);
            reservationRepository.save(reservation);
            inventoryMetrics.recordInventoryExpiredRelease();

            log.info("Released expired inventory reservation, orderId={}, sku={}, quantity={}",
                    reservation.getOrderId(), reservation.getSku(), reservation.getQuantity());
        }
    }
}
