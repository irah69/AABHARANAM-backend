package com.murgan.ecommerce.web.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(

    @NotBlank
    String razorpayOrderId,

    @NotBlank
    String razorpayPaymentId,

    @NotBlank
    String razorpaySignature,

    @NotBlank
    String shippingAddress

) {}