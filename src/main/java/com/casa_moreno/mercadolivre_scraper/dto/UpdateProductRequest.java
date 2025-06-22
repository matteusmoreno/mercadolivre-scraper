package com.casa_moreno.mercadolivre_scraper.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class UpdateProductRequest {
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private String discountPercentage;
    private Integer installments;
    private BigDecimal installmentValue;

}