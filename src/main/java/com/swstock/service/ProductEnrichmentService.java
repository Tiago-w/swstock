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
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

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

        try {
            // 1. Obter VQD token do DuckDuckGo para busca de imagens e web
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

            Pattern vqdPattern = Pattern.compile("vqd=([\\d-]+)");
            Matcher vqdMatcher = vqdPattern.matcher(html);

            if (!vqdMatcher.find()) {
                LOGGER.warning("Não foi possível obter o token VQD para: " + queryLimpa);
                return gerarResultadoFallback(queryLimpa, "Não foi possível conectar ao índice de busca.");
            }

            String vqd = vqdMatcher.group(1);

            // 2. Consultar API de imagens com metadados detalhados
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

            if (imageUrls.isEmpty()) {
                return gerarResultadoFallback(queryLimpa, "Nenhuma imagem encontrada na internet para o termo.");
            }

            // Filtra e limpa as URLs de imagem (prioriza HTTPS e formatos suportados pelo JavaFX)
            List<String> urlsValidas = imageUrls.stream()
                    .map(this::limparUrl)
                    .filter(url -> url.startsWith("http://") || url.startsWith("https://"))
                    .distinct()
                    .toList();

            String fotoPrincipal = urlsValidas.isEmpty() ? null : urlsValidas.get(0);

            // 3. Sintetizar título limpo e descrição rica baseada nos títulos e termos encontrados
            String melhorTitulo = limparTitulo(titles.isEmpty() ? queryLimpa : titles.get(0), queryLimpa);
            String descricaoGerada = sintetizarDescricao(queryLimpa, melhorTitulo, titles);

            return new ProductMediaResult(
                    queryLimpa,
                    melhorTitulo,
                    fotoPrincipal,
                    urlsValidas,
                    descricaoGerada,
                    true,
                    "Foto e descrição obtidas da internet com sucesso!"
            );

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar na internet para o produto: " + queryLimpa, e);
            return gerarResultadoFallback(queryLimpa, "Erro de rede ao consultar a internet: " + e.getMessage());
        }
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
        if (termo.contains("spray") || termo.contains("tinta") || termo.contains("verniz")) return "Insumo Químico / Revestimento";
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

    private String limparUrl(String url) {
        if (url == null) return "";
        return url.replace("\\u0026", "&").replace("\\/", "/").trim();
    }

    private List<String> extrairLista(String json, String regexPattern) {
        List<String> resultados = new ArrayList<>();
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(json);
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
