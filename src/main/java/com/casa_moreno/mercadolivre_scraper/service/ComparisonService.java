package com.casa_moreno.mercadolivre_scraper.service;

import com.casa_moreno.mercadolivre_scraper.domain.ProductInfo;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginRequest;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginResponse;
import com.casa_moreno.mercadolivre_scraper.dto.ProductDetailsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ComparisonService {

    private final CasaMorenoBackendService backendService;
    private final ScrapingService scrapingService;

    @Value("${scraper.admin.username}")
    private String adminUsername;
    @Value("${scraper.admin.password}")
    private String adminPassword;

    public ComparisonService(CasaMorenoBackendService backendService, ScrapingService scrapingService) {
        this.backendService = backendService;
        this.scrapingService = scrapingService;
    }

    public String synchronizeProducts() {
        System.out.println("--- INICIANDO SINCRONIZAÇÃO DE PRODUTOS ---");
        String token = loginAndGetToken();
        List<ProductDetailsResponse> dbProducts = backendService.listAllProducts(token);
        System.out.println(dbProducts.size() + " produtos encontrados. Iniciando verificação...");

        StringBuilder fullReport = new StringBuilder("### RELATÓRIO DE SINCRONIZAÇÃO ###\n\n");

        for (ProductDetailsResponse dbProduct : dbProducts) {
            fullReport.append("--- Verificando: ").append(dbProduct.productTitle()).append(" (ID: ").append(dbProduct.productId()).append(") ---\n");

            if (dbProduct.mercadoLivreUrl() == null || dbProduct.mercadoLivreUrl().isEmpty()) {
                fullReport.append("Status: ERRO - URL do Mercado Livre não encontrada.\n\n");
                continue;
            }

            try {
                ProductInfo scrapedProduct = scrapingService.scrapeProduct(dbProduct.mercadoLivreUrl());

                // Monta um payload dinâmico apenas com os campos que mudaram
                Map<String, Object> updatePayload = buildUpdatePayload(dbProduct, scrapedProduct, fullReport);

                // Se houver algo para atualizar, envia para o backend
                if (!updatePayload.isEmpty()) {
                    // Adiciona o ID do produto ao payload, pois o backend precisa dele para saber qual produto atualizar
                    updatePayload.put("productId", dbProduct.productId());

                    backendService.updateProduct(token, updatePayload);
                    fullReport.append("  -> Status: ATUALIZAÇÃO ENVIADA PARA O BANCO DE DADOS.\n");
                } else {
                    fullReport.append("  -> Status: Sem alterações. Produto sincronizado.\n");
                }

                fullReport.append("\n");

            } catch (Exception e) {
                System.err.println("!!! FALHA no processo para o produto " + dbProduct.productTitle() + ": " + e.getMessage());
                fullReport.append("Status: ERRO - ").append(e.getMessage()).append("\n\n");
            }
        }

        System.out.println("--- SINCRONIZAÇÃO FINALIZADA ---");
        return fullReport.toString();
    }

    private Map<String, Object> buildUpdatePayload(ProductDetailsResponse db, ProductInfo scraped, StringBuilder report) {
        Map<String, Object> payload = new HashMap<>();

        if (isDifferent(db.currentPrice(), scraped.getCurrentPrice())) {
            logDifference("Preço Atual", db.currentPrice(), scraped.getCurrentPrice(), report);
            payload.put("currentPrice", scraped.getCurrentPrice());
        }
        if (isDifferent(db.originalPrice(), scraped.getOriginalPrice())) {
            logDifference("Preço Original", db.originalPrice(), scraped.getOriginalPrice(), report);
            payload.put("originalPrice", scraped.getOriginalPrice());
        }
        if (!Objects.equals(db.installments(), scraped.getInstallments())) {
            logDifference("Qtd. Parcelas", db.installments(), scraped.getInstallments(), report);
            payload.put("installments", scraped.getInstallments());
        }
        if (isDifferent(db.installmentValue(), scraped.getInstallmentValue())) {
            logDifference("Valor da Parcela", db.installmentValue(), scraped.getInstallmentValue(), report);
            payload.put("installmentValue", scraped.getInstallmentValue());
        }
        if (!Objects.equals(db.discountPercentage(), scraped.getDiscountPercentage())) {
            logDifference("Desconto (%)", db.discountPercentage(), scraped.getDiscountPercentage(), report);
            payload.put("discountPercentage", scraped.getDiscountPercentage());
        }

        return payload;
    }

    // Compara BigDecimals ignorando centavos
    private boolean isDifferent(BigDecimal dbValue, BigDecimal scrapedValue) {
        if (dbValue == null || scrapedValue == null) {
            return !Objects.equals(dbValue, scrapedValue);
        }
        return dbValue.longValue() != scrapedValue.longValue();
    }

    private void logDifference(String fieldName, Object dbValue, Object scrapedValue, StringBuilder report) {
        report.append("  - Campo: ").append(fieldName).append(" | Status: DIFERENTE\n");
        report.append("    - BD:     ").append(dbValue).append("\n");
        report.append("    - Scrape: ").append(scrapedValue).append("\n");
    }

    private String loginAndGetToken() {
        CasaMorenoLoginRequest loginRequest = new CasaMorenoLoginRequest(adminUsername, adminPassword);
        ResponseEntity<CasaMorenoLoginResponse> loginResponse = backendService.login(loginRequest);
        return Objects.requireNonNull(loginResponse.getBody()).token();
    }
}