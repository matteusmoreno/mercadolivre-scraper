package com.casa_moreno.mercadolivre_scraper.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@NoArgsConstructor
@Getter @Setter
public class ProductInfo {
    private String name;
    private String description;
    private String brand;
    private BigDecimal price;
    private String category;
    private String subCategory;
    private String imageUrl;
    private String condition;
}
