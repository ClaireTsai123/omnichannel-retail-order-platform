package com.ordering.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long userId;
    private List<CartItemDTO> items;
    private BigDecimal totalPrice;
}
