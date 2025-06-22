package com.casa_moreno.mercadolivre_scraper.service;

import com.casa_moreno.mercadolivre_scraper.client.CasaMorenoBackendClient;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginRequest;
import com.casa_moreno.mercadolivre_scraper.dto.CasaMorenoLoginResponse;
import com.casa_moreno.mercadolivre_scraper.dto.ProductDetailsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CasaMorenoBackendService {

    private final CasaMorenoBackendClient casaMorenoBackendClient;

    public CasaMorenoBackendService(CasaMorenoBackendClient casaMorenoBackendClient) {
        this.casaMorenoBackendClient = casaMorenoBackendClient;
    }

    public ResponseEntity<CasaMorenoLoginResponse> login(@RequestBody CasaMorenoLoginRequest request) {
        return casaMorenoBackendClient.login(request);
    }

    public List<ProductDetailsResponse> listAllProducts(String token) {
        return casaMorenoBackendClient.listAllProducts("Bearer " + token);
    }

    public ProductDetailsResponse findProductById(String token, UUID id) {
        return casaMorenoBackendClient.findProductById("Bearer " + token, id);
    }

    public void updateProduct(String token, Map<String, Object> request) {
        casaMorenoBackendClient.updateProduct("Bearer " + token, request);
    }
}
