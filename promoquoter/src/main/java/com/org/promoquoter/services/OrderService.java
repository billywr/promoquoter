package com.org.promoquoter.services;

import com.org.promoquoter.dto.cart.ConfirmRequest;
import com.org.promoquoter.dto.cart.ConfirmResponse;

/**
 * Order-related operations.
 */
public interface OrderService {
    /**
     * Confirms an order using optional idempotency.
     *
     * @param req     the confirmation request (items, customer segment, etc.)
     * @param idemKey optional idempotency key
     * @return confirmation response containing order id and total
     */
    ConfirmResponse confirm(ConfirmRequest req, String idemKey);
}
