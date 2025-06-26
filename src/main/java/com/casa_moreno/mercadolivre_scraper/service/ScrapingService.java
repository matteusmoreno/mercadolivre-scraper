package com.casa_moreno.mercadolivre_scraper.service;

import com.casa_moreno.mercadolivre_scraper.domain.ProductInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ScrapingService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int TIMEOUT = 15000;

    public ProductInfo scrapeProduct(String url) throws IOException {
        String cleanUrl = url.split("\\?")[0].split("#")[0];
        Document doc = Jsoup.connect(cleanUrl).userAgent(USER_AGENT).timeout(TIMEOUT).get();

        if (isPageUnavailable(doc)) {
            throw new IOException("A página do produto não está mais disponível ou não existe: " + cleanUrl);
        }

        ProductInfo product = new ProductInfo();
        product.setMercadoLivreUrl(url);

        extractProductId(cleanUrl, product);
        extractProductTitle(doc, product, cleanUrl);
        extractFullDescription(doc, product, cleanUrl);
        extractProductBrand(doc, product, cleanUrl);
        extractProductCondition(doc, product, cleanUrl);
        extractPrices(doc, product, cleanUrl); // O método com a correção está aqui
        extractInstallments(doc, product, cleanUrl);
        extractImageUrls(doc, product, cleanUrl);
        extractStockInfo(doc, product, cleanUrl);

        return product;
    }

    private boolean isPageUnavailable(Document doc) {
        Element errorTitle = doc.selectFirst("h2.ui-empty-state__title, .ui-empty-state__title");
        if (errorTitle != null && errorTitle.text().contains("página não existe")) {
            return true;
        }
        Element unavailableStatus = doc.selectFirst(".ui-pdp-status-text-output--paused, .ui-pdp-stock-status__title--paused, .ui-pdp-status-text-output--finalized");
        if (unavailableStatus != null) {
            return true;
        }
        boolean hasBuyButton = doc.selectFirst("button.andes-button--loud, .ui-pdp-actions .andes-button--loud") != null;
        if (!hasBuyButton) {
            return extractText(doc, "span.andes-money-amount__fraction, .ui-pdp-price__main-container span.andes-money-amount__fraction, div[data-testid='price-part'] span.andes-money-amount__fraction") == null;
        }
        return false;
    }

    private void extractProductId(String url, ProductInfo product) {
        Pattern pattern1 = Pattern.compile("(MLB-?\\d+)");
        Matcher matcher1 = pattern1.matcher(url);
        if (matcher1.find()) {
            product.setMercadoLivreId(matcher1.group(1).replace("-", ""));
            return;
        }
        Pattern pattern2 = Pattern.compile("/p/(MLB\\d+)");
        Matcher matcher2 = pattern2.matcher(url);
        if (matcher2.find()) {
            product.setMercadoLivreId(matcher2.group(1));
            return;
        }
        System.err.println("AVISO: ID do produto (MLB) não encontrado na URL: " + url);
    }

    private void extractProductTitle(Document doc, ProductInfo product, String url) {
        String title = extractText(doc, "h1.ui-pdp-title, h1[data-testid='title']");
        product.setProductTitle(title);
        if (product.getProductTitle() == null) {
            System.err.println("AVISO: Título do produto não encontrado para URL: " + url);
        }
    }

    private void extractFullDescription(Document doc, ProductInfo product, String url) {
        String description = extractText(doc, "p[data-testid='content']");
        if (description == null || description.length() < 100) {
            String tempDesc = extractText(doc, "div.ui-pdp-description__content");
            if (tempDesc != null) description = tempDesc;
        }
        product.setFullDescription(description);
    }

    private void extractProductBrand(Document doc, ProductInfo product, String url) {
        String brand = extractSpecValue(doc, "Marca");
        if (brand == null) {
            brand = extractText(doc, "a.ui-pdp-brand-link, a.ui-pdp-specs__table__brand, a[data-testid='action:brand']");
        }
        product.setProductBrand(brand);
    }

    private void extractProductCondition(Document doc, ProductInfo product, String url) {
        String conditionAndSalesText = extractText(doc, "span.ui-pdp-subtitle");
        if (conditionAndSalesText != null) {
            product.setProductCondition(conditionAndSalesText.split("\\|")[0].trim());
        }
    }

    private void extractPrices(Document doc, ProductInfo product, String url) {
        String currentPriceMeta = extractAttribute(doc, "meta[itemprop=price]", "content");
        if (isValidPrice(currentPriceMeta)) {
            product.setCurrentPrice(new BigDecimal(currentPriceMeta));
        } else {
            String currentPriceFraction = extractText(doc, ".ui-pdp-price__main-container span.andes-money-amount__fraction, div[data-testid='price-part'] span.andes-money-amount__fraction");
            String currentPriceCents = extractText(doc, ".ui-pdp-price__main-container span.andes-money-amount__cents, div[data-testid='price-part'] span.andes-money-amount__cents");
            if (currentPriceFraction != null) {
                String priceStr = currentPriceFraction.replaceAll("[^0-9]", "") + "." + (currentPriceCents != null ? currentPriceCents.replaceAll("[^0-9]", "") : "00");
                product.setCurrentPrice(new BigDecimal(priceStr));
            }
        }

        String originalPriceText = extractText(doc, "s.ui-pdp-price__original-value span.andes-money-amount__fraction, .ui-pdp-price__original-value .andes-money-amount__fraction");
        if (originalPriceText != null) {
            String priceStr = originalPriceText.replaceAll("[^0-9]", "");
            String originalPriceCents = extractText(doc, "s.ui-pdp-price__original-value span.andes-money-amount__cents");
            priceStr += "." + (originalPriceCents != null ? originalPriceCents.replaceAll("[^0-9]", "") : "00");
            product.setOriginalPrice(new BigDecimal(priceStr));
        }

        String discount = extractText(doc, "span.ui-pdp-price__second-line__label span.andes-money-amount__discount, .ui-pdp-price__discount-text");
        if (discount != null) {
            // **AQUI ESTÁ A CORREÇÃO**
            // Em vez de remover o texto, agora salvamos o valor completo (ex: "50% OFF").
            product.setDiscountPercentage(discount);
        }

        if (product.getCurrentPrice() == null) {
            System.err.println("AVISO: Preço atual não pôde ser extraído para a URL: " + url);
        }
    }

    private void extractInstallments(Document doc, ProductInfo product, String url) {
        Pattern installmentsPattern = Pattern.compile("(\\d+)x(?: de)?\\s+R\\$\\s*([\\d.,]+)");
        String bodyText = doc.body().text();
        Matcher matcher = installmentsPattern.matcher(bodyText);
        if (matcher.find()) {
            try {
                product.setInstallments(Integer.parseInt(matcher.group(1)));
                String valueStr = matcher.group(2).replaceAll("\\.", "").replace(",", ".");
                product.setInstallmentValue(new BigDecimal(valueStr));
            } catch (NumberFormatException e) {
                System.err.println("ERRO: Erro ao parsear parcelas de '" + matcher.group(0) + "' | URL: " + url);
            }
        }
    }

    private void extractImageUrls(Document doc, ProductInfo product, String url) {
        List<String> imageUrls = doc.select("figure.ui-pdp-gallery__figure img[data-zoom]").stream()
                .map(img -> img.attr("abs:data-zoom")).filter(src -> !src.isEmpty()).collect(Collectors.toList());
        if (imageUrls.isEmpty()) {
            imageUrls = doc.select("div.ui-pdp-gallery__thumbnail__image-container img.ui-pdp-gallery__thumbnail").stream()
                    .map(img -> img.attr("abs:src").replaceAll("(?<=-)[A-Z](?=\\.jpg)", "F")).collect(Collectors.toList());
        }
        product.setGalleryImageUrls(imageUrls);
    }

    private void extractStockInfo(Document doc, ProductInfo product, String url) {
        String stockStatusText = extractText(doc, "p.ui-pdp-stock-information__title, span.ui-pdp-stock-status__title");
        if (stockStatusText != null) {
            product.setStockStatus(stockStatusText);
        } else {
            product.setStockStatus(doc.selectFirst("button.andes-button--loud, .ui-pdp-actions .andes-button--loud") != null ? "Em estoque" : "Status de estoque indisponível");
        }
    }

    private String extractText(Element parent, String cssSelector) {
        Element el = parent.selectFirst(cssSelector);
        return (el != null) ? el.text().trim() : null;
    }

    private String extractAttribute(Element parent, String cssSelector, String attribute) {
        Element el = parent.selectFirst(cssSelector);
        return (el != null && el.hasAttr(attribute)) ? el.attr(attribute).trim() : null;
    }

    private String extractSpecValue(Document doc, String key) {
        for (Element row : doc.select("tr.andes-table__row, tr.ui-vpp-striped-specs__row")) {
            Element th = row.selectFirst("th");
            if (th != null && key.equalsIgnoreCase(th.text().trim())) {
                Element td = row.selectFirst("td span.andes-table__column--value");
                return (td != null) ? td.text().trim() : null;
            }
        }
        return null;
    }

    private boolean isValidPrice(String priceStr) {
        return priceStr != null && !priceStr.isEmpty() && priceStr.matches("\\d+(\\.\\d+)?");
    }
}