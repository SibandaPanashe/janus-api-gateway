package com.sibanda.co.zw.janusgateway.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.pro-price-id}")
    private String proPriceId;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    public String createCheckoutSession(String clientId, String email, String successUrl, String cancelUrl) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomerEmail(email)
                    .setClientReferenceId(clientId)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(proPriceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .putMetadata("clientId", clientId)
                    .build();

            Session session = Session.create(params);
            return session.getUrl();
        } catch (Exception e) {
            log.error("Failed to create Stripe checkout session", e);
            throw new RuntimeException("Stripe session creation failed");
        }
    }

    public String handleWebhook(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(
                    payload, sigHeader, "whsec_placeholder" // Replace with actual webhook secret
            );

            switch (event.getType()) {
                case "checkout.session.completed":
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (session != null) {
                        String clientId = session.getClientReferenceId();
                        log.info("Payment completed for client: {}", clientId);
                        return clientId; // Return clientId to upgrade their plan
                    }
                    break;
                case "customer.subscription.deleted":
                    log.info("Subscription cancelled");
                    break;
            }
            return null;
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            throw new RuntimeException("Webhook error");
        }
    }
}