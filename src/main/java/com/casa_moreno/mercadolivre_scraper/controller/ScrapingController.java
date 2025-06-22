package com.casa_moreno.mercadolivre_scraper.controller;

import com.casa_moreno.mercadolivre_scraper.domain.ProductInfo;
import com.casa_moreno.mercadolivre_scraper.dto.ScrapeRequest;
import com.casa_moreno.mercadolivre_scraper.service.ScrapingService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/scrape")
public class ScrapingController {
    private final ScrapingService scrapingService;

    @Autowired
    public ScrapingController(ScrapingService scrapingService) {
        this.scrapingService = scrapingService;
    }

    @PostMapping
    public ResponseEntity<?> scrapeProduct(@RequestBody ScrapeRequest request) {
        if (request.url() == null || request.url().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("A URL do produto é obrigatória.");
        }

        try {
            ProductInfo productInfo = scrapingService.scrapeProduct(request.url());
            return ResponseEntity.ok(productInfo);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao tentar acessar ou analisar a URL: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ocorreu um erro inesperado: " + e.getMessage());
        }
    }
}
