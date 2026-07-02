package com.ordering.inventoryservice.repository;

import com.ordering.inventoryservice.entity.InventoryReservation;
import com.ordering.inventoryservice.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderId(Long orderId);

    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    Optional<InventoryReservation> findByOrderIdAndSku(Long orderId, String sku);
}
