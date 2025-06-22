package com.casa_moreno.mercadolivre_scraper.controller;

import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginRequest;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginResponse;
import com.casa_moreno.mercadolivre_scraper.dto.ProductDetailsResponse;
import com.casa_moreno.mercadolivre_scraper.service.CasaMorenoBackendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/casa-moreno-backend")
public class CasaMorenoBackendController {

    private final CasaMorenoBackendService casaMorenoBackendService;

    public CasaMorenoBackendController(CasaMorenoBackendService casaMorenoBackendService) {
        this.casaMorenoBackendService = casaMorenoBackendService;
    }

    @PostMapping("/login")
    public ResponseEntity<CasaMorenoLoginResponse> login(@RequestBody CasaMorenoLoginRequest request) {
        return casaMorenoBackendService.login(request);
    }

    @GetMapping("/products/list-all")
    public ResponseEntity<List<ProductDetailsResponse>> listAllProducts() {
        CasaMorenoLoginRequest loginRequest = new CasaMorenoLoginRequest("matteus_moreno", "saquarema123");
        ResponseEntity<CasaMorenoLoginResponse> loginResponse = casaMorenoBackendService.login(loginRequest);

        String token = Objects.requireNonNull(loginResponse.getBody()).token();

        List<ProductDetailsResponse> products = casaMorenoBackendService.listAllProducts(token);
        return ResponseEntity.ok(products);
    }
}
