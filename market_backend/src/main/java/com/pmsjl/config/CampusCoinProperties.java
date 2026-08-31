package com.pmsjl.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "market.campus-coin")
public class CampusCoinProperties {
    private BigDecimal initialBalance = new BigDecimal("1000.00");
    private BigDecimal maxAdminGrant = new BigDecimal("100000.00");

    @PostConstruct
    public void validate() {
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("校园币注册赠送额度不能小于 0");
        }
        if (maxAdminGrant == null || maxAdminGrant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("校园币管理员单次发放上限必须大于 0");
        }
        initialBalance = initialBalance.setScale(2);
        maxAdminGrant = maxAdminGrant.setScale(2);
    }
}
