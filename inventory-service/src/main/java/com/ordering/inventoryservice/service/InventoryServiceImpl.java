package com.ordering.inventoryservice.service;

import com.ordering.common.dto.InventoryReserveItem;
import com.ordering.common.dto.InventoryReserveRequest;
import com.ordering.common.dto.InventoryResponse;
import com.ordering.inventoryservice.entity.Inventory;
import com.ordering.inventoryservice.entity.InventoryReservation;
import com.ordering.inventoryservice.entity.ReservationStatus;
import com.ordering.inventoryservice.repository.InventoryRepository;
import com.ordering.inventoryservice.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    @Override
    @Transactional
    public void reserveInventory(InventoryReserveRequest request) {
        for (InventoryReserveItem item : request.getItems()) {
            InventoryReservation existingReservation = reservationRepository
                    .findByOrderIdAndSku(request.getOrderId(), item.getSku())
                    .orElse(null);
            if (existingReservation != null) {
                if (existingReservation.getStatus() == ReservationStatus.RESERVED
                        || existingReservation.getStatus() == ReservationStatus.COMMITTED) {
                    continue;
                }
                throw new RuntimeException("Inventory reservation already exists for orderId: "
                        + request.getOrderId() + ", sku: " + item.getSku());
            }

            Inventory inventory = inventoryRepository.findBySku(item.getSku())
                    .orElseThrow(() -> new RuntimeException("Inventory not found for sku: " + item.getSku()));

            if (inventory.getAvailableQuantity() < item.getQuantity()) {
                throw new RuntimeException("Not enough inventory for sku: " + item.getSku());
            }
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - item.getQuantity());

            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);

            InventoryReservation reservation = new InventoryReservation();
            reservation.setOrderId(request.getOrderId());
            reservation.setSku(item.getSku());
            reservation.setQuantity(item.getQuantity());
            reservation.setStatus(ReservationStatus.RESERVED);
            reservation.setReservedAt(LocalDateTime.now());
            reservationRepository.save(reservation);
        }
    }

    @Override
    @Transactional
    public void commitInventory(Long orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new RuntimeException("No inventory reservation found for orderId: " + orderId);
        }
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.COMMITTED) {
                continue;
            }
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                throw new RuntimeException("Released inventory reservation cannot be committed for orderId: "
                        + orderId + ", sku: " + reservation.getSku());
            }

            Inventory inventory = inventoryRepository.findBySku(reservation.getSku())
                    .orElseThrow(() -> new RuntimeException("Inventory not found for sku: " + reservation.getSku()));

            if (inventory.getReservedQuantity() < reservation.getQuantity()) {
                throw new RuntimeException("Reserved quantity is inconsistent for sku: " + reservation.getSku());
            }

            inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
            reservation.setStatus(ReservationStatus.COMMITTED);

            inventoryRepository.save(inventory);
            reservationRepository.save(reservation);
        }
    }

    @Override
    @Transactional
    public void releaseInventory(Long orderId) {
      List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new RuntimeException("No inventory reservation found for orderId: " + orderId);
        }
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                continue;
            }
            if (reservation.getStatus() == ReservationStatus.COMMITTED) {
                throw new RuntimeException("Committed inventory reservation cannot be released for orderId: "
                        + orderId + ", sku: " + reservation.getSku());
            }

            Inventory inventory = inventoryRepository.findBySku(reservation.getSku())
                    .orElseThrow(() -> new RuntimeException("Inventory not found for sku: " + reservation.getSku()));
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + reservation.getQuantity());

            if (inventory.getReservedQuantity() < reservation.getQuantity()) {
                throw new RuntimeException("Reserved quantity is inconsistent for sku: " + reservation.getSku());
            }
            inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
            reservation.setStatus(ReservationStatus.RELEASED);

            inventoryRepository.save(inventory);
            reservationRepository.save(reservation);
        }
    }

    @Override
    public InventoryResponse getInventoryBySku(String sku) {
        Inventory inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Inventory not found for sku: " + sku));

        return convertToResponse(inventory);
    }

    private InventoryResponse convertToResponse(Inventory inventory) {
        InventoryResponse response = new InventoryResponse();
        response.setId(inventory.getId());
        response.setSku(inventory.getSku());
        response.setAvailableQuantity(inventory.getAvailableQuantity());
        response.setReserveQuantity(inventory.getReservedQuantity());
        response.setUpdatedAt(inventory.getUpdateAt());
        return response;
    }
}
