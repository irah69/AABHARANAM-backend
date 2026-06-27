package com.murgan.ecommerce.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.murgan.ecommerce.service.PaymentService;
import com.murgan.ecommerce.web.dto.CartDtos.CheckoutRequest;

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

    // Creates a Razorpay Order.
    @PostMapping("/create-order")
    public ResponseEntity<com.razorpay.Order> createOrder(
            @Valid @RequestBody CheckoutRequest request
    ) throws Exception {

        System.out.println("Payment Controller Hit");
        // Ask PaymentService to create a Razorpay Order.
        com.razorpay.Order order =
                paymentService.createRazorpayOrder(
                        request.shippingAddress()
                );

        // Return the Razorpay Order.
        return ResponseEntity.ok(order);
    }
}