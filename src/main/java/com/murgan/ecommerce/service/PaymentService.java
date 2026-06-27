package com.murgan.ecommerce.service;

// Used for representing money.
import java.math.BigDecimal;

// Spring Service annotation.
import org.springframework.stereotype.Service;

import com.murgan.ecommerce.domain.Cart;
// Razorpay SDK classes.
import com.razorpay.RazorpayClient;

// JSON object required by Razorpay SDK.
import org.json.JSONObject;

// Marks this class as a Spring Service.
@Service
public class PaymentService {

    // Used to communicate with Razorpay.
    private final RazorpayClient razorpayClient;

    // Used to validate the cart before creating a payment.
    private final CartService cartService;

    // Constructor Injection.
    public PaymentService(
            RazorpayClient razorpayClient,
            CartService cartService
    ) {

        // Store the injected Razorpay client.
        this.razorpayClient = razorpayClient;

        // Store CartService.
        this.cartService = cartService;
    }
    // Creates a Razorpay Order that the frontend will use to open Razorpay Checkout.
public com.razorpay.Order createRazorpayOrder(String shippingAddress) throws Exception {

    // Validate the cart before creating a payment.
    // This ensures:
    // - Shipping address is valid.
    // - Cart is not empty.
    // - Stock is available.
    Cart cart = cartService.validateCheckout(shippingAddress);

    // Razorpay expects the amount in the smallest currency unit.
    // For INR, that unit is paise.
    // Example:
    // ₹100.50 becomes 10050 paise.
    long amountInPaise = cart.getTotal()
            .multiply(BigDecimal.valueOf(100))
            .longValue();

    // Create a JSON object containing the order details.
    JSONObject options = new JSONObject();

    // Amount in paise.
    options.put("amount", amountInPaise);

    // Currency.
    options.put("currency", "INR");

    // Optional receipt number for your reference.
    options.put("receipt", "receipt_" + System.currentTimeMillis());

    // Create the Razorpay Order.
    return razorpayClient.orders.create(options);
}
}
