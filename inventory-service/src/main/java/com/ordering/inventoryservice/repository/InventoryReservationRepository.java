package com.ordering.inventoryservice.repository;

import com.ordering.inventoryservice.entity.InventoryReservation;
import com.ordering.inventoryservice.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);
}
