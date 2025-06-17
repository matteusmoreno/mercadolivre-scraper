package com.casa_moreno.mercadolivre_scraper.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@NoArgsConstructor
@Getter @Setter
public class ProductInfo {
    private String mercadoLivreId;
    private String mercadoLivreUrl;
    private String productTitle;
    private String fullDescription;
    private String productBrand;
    private String productCondition;
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private String discountPercentage;
    private Integer installments;
    private BigDecimal installmentValue;
    private List<String> galleryImageUrls;
    private String stockStatus;
}