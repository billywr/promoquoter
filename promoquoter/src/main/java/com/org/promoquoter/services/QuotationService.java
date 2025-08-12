package com.org.promoquoter.services;

import com.org.promoquoter.dto.cart.QuoteRequest;
import com.org.promoquoter.dto.cart.QuoteResponse;

/**
 * Service interface for generating product quotations with applied promotions.
 */
public interface QuotationService {

    /**
     * Generates a quote for the given cart request, applying all applicable promotions.
     *
     * @param req the quote request containing cart items and optional customer segment
     * @return the computed quote response with item breakdowns, applied promotions, and totals
     */
    QuoteResponse quote(QuoteRequest req);
}
