package com.sibanda.co.zw.janusgateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfile implements Serializable {

    private String clientId;
    private String apiKeyHash;
    private String plan;              // "free", "pro", "enterprise"
    private int requestCountSecond;
    private int requestCountMinute;
    private int limitPerSecond;
    private int limitPerMinute;
    private boolean blocked;
    private double surchargeAmount;   // for pay-as-you-go billing
    private Instant lastResetTime;
}