package com.casa_moreno.mercadolivre_scraper.controller;

import com.casa_moreno.mercadolivre_scraper.service.ComparisonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @PostMapping("/all")
    public ResponseEntity<String> synchronizeAllProducts() {
        try {
            String report = comparisonService.synchronizeProducts();
            return ResponseEntity.ok().header("Content-Type", "text/plain; charset=utf-8").body(report);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Ocorreu um erro geral durante a sincronização: " + e.getMessage());
        }
    }
}