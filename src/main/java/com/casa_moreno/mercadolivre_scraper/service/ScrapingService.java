package com.casa_moreno.mercadolivre_scraper.service;

import com.casa_moreno.mercadolivre_scraper.domain.ProductInfo;
import org.springframework.stereotype.Service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScrapingService {
    public ProductInfo scrapeProduct(String url) throws IOException {
        // É crucial simular um navegador para evitar bloqueios
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .get();

        ProductInfo product = new ProductInfo();

        // Nome
        product.setName(extractText(doc, "h1.ui-pdp-title"));

        // Descrição
        product.setDescription(extractText(doc, "p.ui-pdp-description__content"));

        // Condição (Novo/Usado)
        String conditionText = extractText(doc, "span.ui-pdp-subtitle");
        if (conditionText != null) {
            // O texto pode ser "Novo | 123 vendidos". Pegamos a parte antes do "|" e limpamos espaços.
            String condition = conditionText.split("\\|")[0].trim();
            product.setCondition(condition);
        }

        // URL da Imagem
        product.setImageUrl(extractAttribute(doc, "img.ui-pdp-image__figure__image", "src"));

        // Preço
        String priceFraction = extractText(doc, "span.andes-money-amount__fraction");
        String priceCents = extractText(doc, "span.andes-money-amount__cents");
        if (priceFraction != null) {
            String priceStr = priceFraction.replaceAll("\\.", "") + "." + (priceCents != null ? priceCents : "00");
            product.setPrice(new BigDecimal(priceStr));
        }

        // Categorias (Breadcrumb)
        Elements breadcrumbLinks = doc.select("a.andes-breadcrumb__link");
        List<String> categories = breadcrumbLinks.stream().map(Element::text).collect(Collectors.toList());
        if (!categories.isEmpty()) {
            product.setCategory(categories.get(0));
            if (categories.size() > 1) {
                product.setSubCategory(categories.get(1));
            }
        }

        // Marca (da tabela de características)
        Element specsTable = doc.selectFirst("table.andes-table");
        if (specsTable != null) {
            for (Element row : specsTable.select("tr")) {
                Element header = row.selectFirst("th");
                if (header != null && "Marca".equalsIgnoreCase(header.text())) {
                    product.setBrand(extractText(row, "td span.andes-table__column--value"));
                    break;
                }
            }
        }

        // Se a marca não foi encontrada na tabela, tente uma alternativa
        if (product.getBrand() == null) {
            product.setBrand(extractText(doc, "a.ui-pdp-brand-link"));
        }

        return product;
    }

    // Funções auxiliares para evitar NullPointerException e limpar o código
    private String extractText(Element parent, String cssSelector) {
        Element el = parent.selectFirst(cssSelector);
        return (el != null) ? el.text().trim() : null;
    }

    private String extractAttribute(Element parent, String cssSelector, String attribute) {
        Element el = parent.selectFirst(cssSelector);
        return (el != null) ? el.attr(attribute) : null;
    }
}
