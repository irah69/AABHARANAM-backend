package com.murgan.ecommerce.web;

import java.util.HashMap;
import java.util.Map;
import com.murgan.ecommerce.web.dto.VerifyPaymentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.murgan.ecommerce.service.PaymentService;
import com.murgan.ecommerce.web.dto.CartDtos.CheckoutRequest;

import com.razorpay.Order;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    // Service responsible for Razorpay operations.
    private final PaymentService paymentService;

    // Constructor Injection.
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
    @PostMapping("/verify")
public ResponseEntity<Map<String, String>> verifyPayment(
        @Valid @RequestBody VerifyPaymentRequest request
) throws Exception {

    paymentService.verifyPayment(request);

    return ResponseEntity.ok(
            Map.of("message", "Payment verified successfully")
    );
}
    // Creates a Razorpay Order.
    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(
            @Valid @RequestBody CheckoutRequest request
    ) throws Exception {

        System.out.println("Payment Controller Hit");

        // Create Razorpay order
        Order order = paymentService.createRazorpayOrder(
                request.shippingAddress()
        );

        // Build a JSON response
        Map<String, Object> response = new HashMap<>();
        response.put("id", order.get("id"));
        response.put("amount", order.get("amount"));
        response.put("currency", order.get("currency"));
        response.put("status", order.get("status"));
        response.put("receipt", order.get("receipt"));

        return ResponseEntity.ok(response);
    }
    
}