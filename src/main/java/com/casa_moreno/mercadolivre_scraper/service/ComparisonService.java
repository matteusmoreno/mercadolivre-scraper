package com.casa_moreno.mercadolivre_scraper.service;

import com.casa_moreno.mercadolivre_scraper.domain.ProductInfo;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginRequest;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginResponse;
import com.casa_moreno.mercadolivre_scraper.dto.ProductDetailsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                fullReport.append("Status: ERRO - URL do Mercado Livre não encontrada no banco de dados.\n\n");
                continue;
            }

            try {
                ProductInfo scrapedProduct = scrapingService.scrapeProduct(dbProduct.mercadoLivreUrl());
                Map<String, Object> updatePayload = buildUpdatePayload(dbProduct, scrapedProduct, fullReport);

                if (!updatePayload.isEmpty()) {
                    updatePayload.put("productId", dbProduct.productId());
                    backendService.updateProduct(token, updatePayload);
                    fullReport.append("  -> Status: ATUALIZAÇÃO ENVIADA PARA O BANCO DE DADOS.\n");
                } else {
                    fullReport.append("  -> Status: Sem alterações. Produto sincronizado.\n");
                }
                fullReport.append("\n");

            } catch (IOException e) {
                if (e.getMessage().contains("página do produto não está mais disponível")) {
                    System.out.println("AVISO: Produto " + dbProduct.productTitle() + " parece estar indisponível. " + e.getMessage());
                    fullReport.append("Status: AVISO - Produto indisponível ou página removida.\n\n");
                } else {
                    System.err.println("!!! FALHA DE CONEXÃO para o produto " + dbProduct.productTitle() + ": " + e.getMessage());
                    fullReport.append("Status: ERRO DE CONEXÃO - ").append(e.getMessage()).append("\n\n");
                }
            } catch (Exception e) {
                System.err.println("!!! FALHA GERAL no processo para o produto " + dbProduct.productTitle() + ": " + e.getMessage());
                fullReport.append("Status: ERRO INESPERADO - ").append(e.getMessage()).append("\n\n");
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
        String dbDiscount = db.discountPercentage() != null ? db.discountPercentage().replaceAll("[^0-9]", "") : null;
        String scrapedDiscount = scraped.getDiscountPercentage() != null ? scraped.getDiscountPercentage().replaceAll("[^0-9]", "") : null;
        if (!Objects.equals(dbDiscount, scrapedDiscount)) {
            logDifference("Desconto (%)", db.discountPercentage(), scraped.getDiscountPercentage(), report);
            payload.put("discountPercentage", scraped.getDiscountPercentage());
        }

        return payload;
    }

    /**
     * **MODIFICADO**: Compara BigDecimals ignorando diferenças de centavos.
     * Isso evita atualizações desnecessárias quando o preço muda de 1809.00 para 1809.03, por exemplo.
     */
    private boolean isDifferent(BigDecimal dbValue, BigDecimal scrapedValue) {
        if (dbValue == null && scrapedValue == null) return false;
        if (dbValue == null || scrapedValue == null) return true;
        // Compara apenas a parte inteira do valor.
        return dbValue.longValue() != scrapedValue.longValue();
    }

    private void logDifference(String fieldName, Object dbValue, Object scrapedValue, StringBuilder report) {
        report.append("  - Campo: ").append(fieldName).append(" | Status: DIFERENTE\n");
        report.append("    - Valor no BD:   ").append(dbValue == null ? "N/A" : dbValue).append("\n");
        report.append("    - Valor Scraped: ").append(scrapedValue == null ? "N/A" : scrapedValue).append("\n");
    }

    private String loginAndGetToken() {
        CasaMorenoLoginRequest loginRequest = new CasaMorenoLoginRequest(adminUsername, adminPassword);
        ResponseEntity<CasaMorenoLoginResponse> loginResponse = backendService.login(loginRequest);
        return Objects.requireNonNull(loginResponse.getBody()).token();
    }
}