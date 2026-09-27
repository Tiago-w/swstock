package com.swstock.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço responsável por buscar imagens reais e dados técnicos de produtos na internet
 * e sintetizar descrições comerciais e técnicas automatizadas.
 */
public class ProductEnrichmentService {

    private static final Logger LOGGER = Logger.getLogger(ProductEnrichmentService.class.getName());
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient;

    public record ProductMediaResult(
            String query,
            String suggestedTitle,
            String primaryImageUrl,
            List<String> candidateImageUrls,
            String generatedDescription,
            boolean success,
            String statusMessage
    ) {}

    public ProductEnrichmentService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Busca na internet fotos e metadados para o termo informado e gera a descrição.
     */
    public ProductMediaResult buscarEEnriquecer(String termoBusca) {
        if (termoBusca == null || termoBusca.trim().isEmpty()) {
            return new ProductMediaResult("", "", null, List.of(), "", false, "Termo de busca vazio.");
        }

        String queryLimpa = termoBusca.trim();
        LOGGER.info("Iniciando busca web para enriquecimento do produto: " + queryLimpa);

        // 1. Tentar busca via Bing Images Async (rápida, fotos reais em alta definição)
        ProductMediaResult resultadoBing = buscarViaBing(queryLimpa);
        if (resultadoBing != null && resultadoBing.success()) {
            return resultadoBing;
        }

        // 2. Fallback via DuckDuckGo com headers e regex resilientes
        ProductMediaResult resultadoDuck = buscarViaDuckDuckGo(queryLimpa);
        if (resultadoDuck != null && resultadoDuck.success()) {
            return resultadoDuck;
        }

        // 3. Fallback informativo caso ambos os motores estejam indisponíveis
        return gerarResultadoFallback(queryLimpa, "Nenhuma imagem encontrada na internet para o termo pesquisado.");
    }

    private ProductMediaResult buscarViaBing(String queryLimpa) {
        try {
            String encoded = URLEncoder.encode(queryLimpa, StandardCharsets.UTF_8);
            String url = "https://www.bing.com/images/async?q=" + encoded + "&first=1&count=20";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String html = resp.body();

                List<String> rawUrls = extrairLista(html, "murl&quot;:&quot;([^&]+)&quot;");
                List<String> rawTitles = extrairLista(html, "&quot;t&quot;:&quot;([^&]+)&quot;");

                if (!rawUrls.isEmpty()) {
                    List<String> urlsValidas = rawUrls.stream()
                            .map(this::desescaparHtml)
                            .map(this::limparUrl)
                            .filter(u -> u.startsWith("http://") || u.startsWith("https://"))
                            .distinct()
                            .toList();

                    List<String> titulosLimpos = rawTitles.stream()
                            .map(this::desescaparHtml)
                            .map(t -> limparTitulo(t, queryLimpa))
                            .toList();

                    if (!urlsValidas.isEmpty()) {
                        String fotoPrincipal = urlsValidas.get(0);
                        String melhorTitulo = titulosLimpos.isEmpty() ? queryLimpa : titulosLimpos.get(0);
                        String descricao = sintetizarDescricao(queryLimpa, melhorTitulo, titulosLimpos);

                        return new ProductMediaResult(
                                queryLimpa,
                                melhorTitulo,
                                fotoPrincipal,
                                urlsValidas,
                                descricao,
                                true,
                                "Fotos e detalhes obtidos com sucesso via Bing Images!"
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha na busca Bing Images para: " + queryLimpa + ": " + e.getMessage());
        }
        return null;
    }

    private ProductMediaResult buscarViaDuckDuckGo(String queryLimpa) {
        try {
            String encoded = URLEncoder.encode(queryLimpa, StandardCharsets.UTF_8);
            String initialUrl = "https://duckduckgo.com/?q=" + encoded;

            HttpRequest initReq = HttpRequest.newBuilder()
                    .uri(URI.create(initialUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> initResp = httpClient.send(initReq, HttpResponse.BodyHandlers.ofString());
            String html = initResp.body();

            Pattern vqdPattern = Pattern.compile("vqd=[\"']?([\\d-]+)[\"']?");
            Matcher vqdMatcher = vqdPattern.matcher(html);

            if (vqdMatcher.find()) {
                String vqd = vqdMatcher.group(1);
                String imgApiUrl = "https://duckduckgo.com/i.js?l=wt-wt&o=json&q=" + encoded + "&vqd=" + vqd + "&f=,,,&p=1";

                HttpRequest imgReq = HttpRequest.newBuilder()
                        .uri(URI.create(imgApiUrl))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();

                HttpResponse<String> imgResp = httpClient.send(imgReq, HttpResponse.BodyHandlers.ofString());
                String jsonResp = imgResp.body();

                List<String> imageUrls = extrairLista(jsonResp, "\"image\":\"([^\"]+)\"");
                List<String> titles = extrairLista(jsonResp, "\"title\":\"([^\"]+)\"");

                if (!imageUrls.isEmpty()) {
                    List<String> urlsValidas = imageUrls.stream()
                            .map(this::limparUrl)
                            .filter(url -> url.startsWith("http://") || url.startsWith("https://"))
                            .distinct()
                            .toList();

                    if (!urlsValidas.isEmpty()) {
                        String fotoPrincipal = urlsValidas.get(0);
                        String melhorTitulo = limparTitulo(titles.isEmpty() ? queryLimpa : titles.get(0), queryLimpa);
                        String descricao = sintetizarDescricao(queryLimpa, melhorTitulo, titles);

                        return new ProductMediaResult(
                                queryLimpa,
                                melhorTitulo,
                                fotoPrincipal,
                                urlsValidas,
                                descricao,
                                true,
                                "Fotos e detalhes obtidos via DuckDuckGo!"
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha na busca DuckDuckGo para: " + queryLimpa + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Sintetiza uma descrição comercial precisa e profissional baseada no item e no contexto da web.
     */
    private String sintetizarDescricao(String termoOriginal, String tituloEncontrado, List<String> titulosRelacionados) {
        StringBuilder desc = new StringBuilder();

        String termoLower = termoOriginal.toLowerCase();
        String tituloLower = tituloEncontrado.toLowerCase();

        // Identificação de marcas e categorias comuns
        String marcaIdentificada = extrairMarca(termoOriginal, titulosRelacionados);
        String categoria = identificarCategoria(termoLower, tituloLower);

        desc.append(tituloEncontrado).append(".\n\n");

        if (termoLower.contains("spray") || tituloLower.contains("spray") || termoLower.contains("etaniz")) {
            desc.append("Produto em formato aerossol de alta performance com válvula de aplicação precisa. ");
            if (tituloLower.contains("alta temperatura") || termoLower.contains("temperatura")) {
                desc.append("Formulado para suportar elevadas temperaturas, ideal para escapamentos, motores e superfícies metálicas expostas ao calor. ");
            } else if (tituloLower.contains("desengripante") || termoLower.contains("desengripante")) {
                desc.append("Ação desengripante com alto poder de penetração, lubrificação contínua e proteção contra oxidação e ferrugem. ");
            } else {
                desc.append("Fórmula de secagem rápida com excelente cobertura, resistência a intempéries e acabamento uniforme em ambientes internos e externos. ");
            }
            if (marcaIdentificada != null) {
                desc.append("Fabricado com os padrões de qualidade da marca ").append(marcaIdentificada).append(". ");
            }
        } else if (termoLower.contains("mouse") || termoLower.contains("teclado") || termoLower.contains("headset") || termoLower.contains("webcam")) {
            desc.append("Dispositivo periférico com conexão de alta velocidade e design ergonômico, projetado para durabilidade e resposta rápida em ambientes corporativos e gamer. ");
        } else if (termoLower.contains("cabo") || termoLower.contains("adaptador") || termoLower.contains("hub")) {
            desc.append("Acessório com condutores de alto rendimento, blindagem eletromagnética e excelente transferência de sinal/dados. ");
        } else {
            desc.append(categoria).append(" de alto rendimento. Atende às especificações técnicas vigentes, proporcionando confiabilidade, excelente durabilidade e pronta disponibilidade para expedição. ");
        }

        desc.append("\n• Aplicação: Uso profissional, comercial e industrial.");
        desc.append("\n• Origem: Estoque físico verificado no sistema SWStock.");

        return desc.toString();
    }

    private String extrairMarca(String termoOriginal, List<String> titulos) {
        String lower = termoOriginal.toLowerCase();
        if (lower.contains("etaniz")) return "Etaniz";
        if (lower.contains("wanda")) return "Wanda";
        if (lower.contains("lazzuril")) return "Lazzuril";
        if (lower.contains("suvinil")) return "Suvinil";
        if (lower.contains("coral")) return "Coral";
        if (lower.contains("sherwin")) return "Sherwin Williams";
        if (lower.contains("skol")) return "Skol";
        if (lower.contains("logitech")) return "Logitech";
        if (lower.contains("redragon")) return "Redragon";
        if (lower.contains("makita")) return "Makita";
        if (lower.contains("bosch")) return "Bosch";
        if (lower.contains("wd-40") || lower.contains("wd 40")) return "WD-40";
        if (lower.contains("3m")) return "3M";
        if (lower.contains("intelbras")) return "Intelbras";
        return null;
    }

    private String identificarCategoria(String termo, String titulo) {
        if (termo.contains("spray") || termo.contains("tinta") || termo.contains("verniz") || termo.contains("base")) return "Insumo Químico / Revestimento";
        if (termo.contains("oleo") || termo.contains("graxa") || termo.contains("lubrificante")) return "Lubrificante e Manutenção";
        if (termo.contains("ferramenta") || termo.contains("chave") || termo.contains("furadeira")) return "Ferramenta e Equipamento";
        if (termo.contains("eletronico") || termo.contains("cabo") || termo.contains("placa")) return "Eletrônico / Hardware";
        return "Item de Estoque";
    }

    private String limparTitulo(String rawTitle, String fallback) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            return fallback;
        }
        // Remove sufixos de marketplaces comuns (e.g. "| Shopee Brasil", "- Mercado Livre", etc.)
        String limpo = rawTitle.replaceAll("(?i)\\s*(\\||-|–)\\s*(Shopee|Mercado Livre|Amazon|Magazine Luiza|Americanas|Leroy Merlin|Aliexpress).*", "")
                .replaceAll("<[^>]+>", "")
                .trim();
        return limpo.isEmpty() ? fallback : limpo;
    }

    private String desescaparHtml(String str) {
        if (str == null) return "";
        return str.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    private String limparUrl(String url) {
        if (url == null) return "";
        return url.replace("\\u0026", "&").replace("\\/", "/").trim();
    }

    private List<String> extrairLista(String texto, String regexPattern) {
        List<String> resultados = new ArrayList<>();
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(texto);
        while (matcher.find()) {
            resultados.add(matcher.group(1));
        }
        return resultados;
    }

    private ProductMediaResult gerarResultadoFallback(String query, String motivo) {
        String desc = String.format("Item cadastrado: %s. Produto em conformidade com o estoque SWStock.", query);
        return new ProductMediaResult(query, query, null, List.of(), desc, false, motivo);
    }
}
