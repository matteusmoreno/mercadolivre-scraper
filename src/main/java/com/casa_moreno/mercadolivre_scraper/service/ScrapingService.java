package com.casa_moreno.mercadolivre_scraper.service;

import com.casa_moreno.mercadolivre_scraper.domain.ProductInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ScrapingService {

    public ProductInfo scrapeProduct(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(15000)
                .get();

        ProductInfo product = new ProductInfo();
        product.setMercadoLivreUrl(url);

        extractProductId(url, product);
        extractProductTitle(doc, product, url);
        extractFullDescription(doc, product, url);
        extractProductBrand(doc, product, url);
        extractProductCondition(doc, product, url);
        extractPrices(doc, product, url);
        extractInstallments(doc, product, url);
        extractImageUrls(doc, product, url);
        extractStockInfo(doc, product, url); // Esta função ainda existe, mas não fará mais a extração de quantity.

        return product;
    }

    /** Extrai o ID do produto da URL. */
    private void extractProductId(String url, ProductInfo product) {
        Pattern pattern = Pattern.compile("MLB(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            product.setMercadoLivreId("MLB" + matcher.group(1));
        } else {
            System.err.println("WARN: ID do produto não encontrado na URL: " + url);
        }
    }

    /** Extrai o título do produto. */
    private void extractProductTitle(Document doc, ProductInfo product, String url) {
        product.setProductTitle(extractText(doc, "h1.ui-pdp-title"));
        if (product.getProductTitle() == null) {
            System.err.println("WARN: Título do produto não encontrado para URL: " + url);
        }
    }

    /** Extrai a descrição completa do produto. */
    private void extractFullDescription(Document doc, ProductInfo product, String url) {
        String description = null;

        // Tenta pegar o texto puro da tag <p data-testid="content">
        Element descriptionContentParagraph = doc.selectFirst("p[data-testid='content']");
        if (descriptionContentParagraph != null) {
            description = descriptionContentParagraph.text().trim();
        }

        // Se o parágrafo principal não deu resultado ou está muito curto, tenta o contêiner geral.
        if (description == null || description.isEmpty() || description.length() < 100) {
            Element descriptionContainer = doc.selectFirst("div.ui-pdp-description__content");
            if (descriptionContainer != null) {
                description = descriptionContainer.text().trim();
            }
        }

        // Fallback: Se ainda assim a descrição estiver vazia ou curta, tenta seguir o link "Ver descrição completa"
        if (description == null || description.isEmpty() || description.length() < 100) {
            Element fullDescriptionLink = doc.selectFirst("a.ui-pdp-collapsable__action[title='Ver descrição completa']");

            if (fullDescriptionLink != null) {
                String descriptionUrl = fullDescriptionLink.absUrl("href");
                if (descriptionUrl != null && !descriptionUrl.isEmpty()) {
                    try {
                        Document descriptionDoc = Jsoup.connect(descriptionUrl)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                .timeout(10000)
                                .get();

                        Element externalDescContent = descriptionDoc.selectFirst("div.item-description__text");
                        if (externalDescContent == null) {
                            externalDescContent = descriptionDoc.selectFirst("div.ui-pdp-description__content");
                        }
                        if (externalDescContent == null) {
                            externalDescContent = descriptionDoc.selectFirst("div.description-content");
                        }

                        if (externalDescContent != null) {
                            description = externalDescContent.text().trim();
                        } else {
                            System.out.println("INFO: Conteúdo da descrição na página separada não encontrado para URL: " + descriptionUrl);
                        }

                    } catch (IOException e) {
                        System.err.println("ERROR: Erro ao carregar ou parsear a página de descrição completa para URL: " + descriptionUrl + " | Erro: " + e.getMessage());
                    }
                }
            }
        }

        if (description != null) {
            description = description.replaceAll("\\s+", " ");
            description = description.replaceAll("&amp;", "&");
        }

        product.setFullDescription(description);
        if (product.getFullDescription() == null || product.getFullDescription().isEmpty()) {
            System.out.println("INFO: Descrição do produto não encontrada ou vazia para URL: " + url);
        }
    }

    /** Extrai a marca do produto. */
    private void extractProductBrand(Document doc, ProductInfo product, String url) {
        // A extração da marca da tabela de especificações ainda usa extractSpecValue.
        // Se você não quiser *nenhuma* extração baseada em especificações, você pode
        // remover a chamada a extractSpecValue e o metodo extractSpecValue auxiliar.
        product.setProductBrand(extractSpecValue(doc, "Marca"));
        if (product.getProductBrand() == null) {
            Element sellerBrand = doc.selectFirst("span.ui-pdp-seller__brand-title span.ui-pdp-family--SEMIBOLD");
            if (sellerBrand != null) {
                product.setProductBrand(sellerBrand.text().trim());
            } else {
                System.err.println("WARN: Marca do produto não encontrada para URL: " + url);
            }
        }
    }

    /** Extrai a condição do produto (Novo/Usado). */
    private void extractProductCondition(Document doc, ProductInfo product, String url) {
        String conditionAndSalesText = extractText(doc, "span.ui-pdp-subtitle");
        if (conditionAndSalesText != null) {
            String condition = conditionAndSalesText.split("\\|")[0].trim();
            product.setProductCondition(condition);
        } else {
            System.err.println("WARN: Condição do produto não encontrada para URL: " + url);
        }
    }

    /** Extrai o preço atual, preço original e percentual de desconto. */
    private void extractPrices(Document doc, ProductInfo product, String url) {
        String currentPriceMeta = extractAttribute(doc, "meta[itemprop=price]", "content");
        if (currentPriceMeta != null && !currentPriceMeta.isEmpty()) {
            try {
                product.setCurrentPrice(new BigDecimal(currentPriceMeta));
            } catch (NumberFormatException e) {
                System.err.println("ERROR: Erro ao parsear currentPrice da meta tag: '" + currentPriceMeta + "' | URL: " + url + " | Erro: " + e.getMessage());
            }
        } else {
            String currentPriceFraction = extractText(doc, "span.andes-money-amount__fraction");
            String currentPriceCents = extractText(doc, "span.andes-money-amount__cents");

            if (currentPriceFraction != null) {
                String priceStr = currentPriceFraction.replaceAll("\\.", "");
                if (currentPriceCents != null && !currentPriceCents.isEmpty()) {
                    priceStr += "." + currentPriceCents;
                } else {
                    priceStr += ".00";
                }
                try {
                    product.setCurrentPrice(new BigDecimal(priceStr));
                } catch (NumberFormatException e) {
                    System.err.println("ERROR: Erro ao parsear currentPrice (fallback): '" + priceStr + "' | URL: " + url + " | Erro: " + e.getMessage());
                }
            } else {
                System.err.println("WARN: Preço atual não encontrado (nem meta tag, nem fração/centavos) para URL: " + url);
            }
        }

        String originalPriceFraction = extractText(doc, "s.ui-pdp-price__original-value span.andes-money-amount__fraction");
        if (originalPriceFraction != null) {
            try {
                product.setOriginalPrice(new BigDecimal(originalPriceFraction.replaceAll("\\.", "") + ".00"));
            } catch (NumberFormatException e) {
                System.err.println("ERROR: Erro ao parsear originalPrice: '" + originalPriceFraction + "' | URL: " + url + " | Erro: " + e.getMessage());
            }
        } else {
            System.out.println("INFO: Preço original riscado não encontrado para URL: " + url);
        }

        product.setDiscountPercentage(extractText(doc, "span.ui-pdp-price__second-line__label span.andes-money-amount__discount"));
        if (product.getDiscountPercentage() == null) {
            System.out.println("INFO: Percentual de desconto não encontrado para URL: " + url);
        }
    }

    /** Extrai informações de parcelamento. */
    private void extractInstallments(Document doc, ProductInfo product, String url) {
        String installmentsFullText = extractText(doc, "p#pricing_price_subtitle");
        if (installmentsFullText != null) {
            Pattern installmentsPattern = Pattern.compile("em\\s+(\\d+)x\\s+R\\$\\s*([\\d.,]+)");
            Matcher installmentsMatcher = installmentsPattern.matcher(installmentsFullText);
            if (installmentsMatcher.find()) {
                try {
                    product.setInstallments(Integer.parseInt(installmentsMatcher.group(1)));
                    String valueStr = installmentsMatcher.group(2).replaceAll("\\.", "").replace(",", ".");
                    product.setInstallmentValue(new BigDecimal(valueStr));
                } catch (NumberFormatException e) {
                    System.err.println("ERROR: Erro ao parsear installments/installmentValue: '" + installmentsFullText + "' | URL: " + url + " | Erro: " + e.getMessage());
                }
            } else {
                System.out.println("INFO: Padrão de parcelas não encontrado no texto: '" + installmentsFullText + "' para URL: " + url);
            }
        } else {
            System.out.println("INFO: Texto de parcelas não encontrado para URL: " + url);
        }
    }

    /** Extrai URLs da imagem principal e da galeria. */
    private void extractImageUrls(Document doc, ProductInfo product, String url) {
        Elements galleryElements = doc.select("figure.ui-pdp-gallery__figure img[data-zoom]");
        if (!galleryElements.isEmpty()) {
            product.setGalleryImageUrls(galleryElements.stream()
                    .map(img -> img.attr("data-zoom"))
                    .filter(src -> !src.isEmpty())
                    .collect(Collectors.toList()));
        } else {
            System.out.println("INFO: Imagens da galeria não encontradas (data-zoom) para URL: " + url);
            Elements thumbnailElements = doc.select("span.ui-pdp-gallery__thumbnail-wrapper img.ui-pdp-gallery__thumbnail-img");
            product.setGalleryImageUrls(thumbnailElements.stream()
                    .map(img -> img.attr("src").replace("-50x50.jpg", "-400xN.jpg"))
                    .filter(src -> !src.isEmpty())
                    .collect(Collectors.toList()));
        }
    }

    /** Extrai o status do estoque e a quantidade disponível (apenas status agora). */
    private void extractStockInfo(Document doc, ProductInfo product, String url) {
        String stockStatusText = extractText(doc, "p.ui-pdp-stock-information__title");
        if (stockStatusText != null) {
            product.setStockStatus(stockStatusText.trim());
        } else {
            if (doc.selectFirst("button.andes-button.ui-pdp-action--primary") != null) {
                product.setStockStatus("Em estoque");
            } else {
                product.setStockStatus("Status de estoque desconhecido ou indisponível");
            }
            System.out.println("INFO: Status de estoque não encontrado diretamente, inferindo para URL: " + url + " -> " + product.getStockStatus());
        }
    }


    /** Extrai o texto de um elemento com base no seletor CSS. */
    private String extractText(Element parent, String cssSelector) {
        Element el = parent.selectFirst(cssSelector);
        return (el != null) ? el.text().trim() : null;
    }

    /** Extrai o valor de um atributo de um elemento com base no seletor CSS. */
    private String extractAttribute(Element parent, String cssSelector, String attribute) {
        Element el = parent.selectFirst(cssSelector);
        return (el != null && el.hasAttr(attribute)) ? el.attr(attribute).trim() : null;
    }

    private String extractSpecValue(Document doc, String key) {
        Elements specRows = doc.select("div.ui-vpp-striped-specs__table tr, table.andes-table tr, section.ui-pdp-specs__table-container tr");
        for (Element row : specRows) {
            String header = extractText(row, "th.andes-table__header");
            if (header != null && key.equalsIgnoreCase(header.trim())) {
                return extractText(row, "td.andes-table__column--value");
            }
        }
        return null;
    }
}