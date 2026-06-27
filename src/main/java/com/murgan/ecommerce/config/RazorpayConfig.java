package com.murgan.ecommerce.config;

// Reads values from application.yml
import org.springframework.beans.factory.annotation.Value;

// Declares this class as a configuration class.
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Razorpay Java SDK
import com.razorpay.RazorpayClient;

// Configuration class that creates reusable beans.
@Configuration
public class RazorpayConfig {

    // Reads razorpay.key-id from application.yml
    @Value("${razorpay.key-id}")
    private String keyId;

    // Reads razorpay.key-secret from application.yml
    @Value("${razorpay.key-secret}")
    private String keySecret;

    // Creates one RazorpayClient bean for the entire application.
    @Bean
    public RazorpayClient razorpayClient() throws Exception {

        // Creates the client using your Test Key ID and Secret.
        return new RazorpayClient(keyId, keySecret);
    }
}