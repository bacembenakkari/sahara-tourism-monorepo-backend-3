package com.camping.duneinsolite.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.currency")
public class CurrencyConfig {

    /** How many TND equal 1 EUR  (TND ÷ eurRate → EUR) */
    private double eurRate = 3.4;

    /** How many TND equal 1 USD  (TND ÷ usdRate → USD) */
    private double usdRate = 2.5;
}
