package com.swstock.service;

import com.swstock.database.ProdutoDAO;
import com.swstock.model.Produto;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serviço de Importação e Exportação de Produtos via arquivos XML.
 * Realiza parsing inteligente, sincronização de catálogo e preservação de estoque físico.
 */
public class XmlService {

    private static final Logger LOGGER = Logger.getLogger(XmlService.class.getName());
    private final ProdutoDAO produtoDAO;

    public XmlService() {
        this(new ProdutoDAO());
    }

    public XmlService(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    /**
     * Resultado enriquecido da operação de importação de XML.
     */
    public record ImportResult(
            int totalLidos,
            int totalProcessados,
            int novosInseridos,
            int atualizadosPreservados,
            int precosAlterados,
            int nomesAlterados,
            int itensMantidosNoBancoNaoPresentesNoXml,
            List<String> erros
    ) {
        public boolean isSucesso() {
            return erros.isEmpty() && totalProcessados > 0;
        }
    }

    /**
     * Importa produtos de um arquivo XML com sincronização inteligente.
     */
    public ImportResult importarProdutos(File xmlFile) throws Exception {
        if (xmlFile == null || !xmlFile.exists() || !xmlFile.canRead()) {
            throw new IllegalArgumentException("Arquivo XML inválido ou inacessível.");
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        List<Produto> produtosParaInserirOuAtualizar = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        Set<String> skusNoXml = new HashSet<>();

        NodeList nList = doc.getElementsByTagName("produto");

        for (int i = 0; i < nList.getLength(); i++) {
            Node node = nList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                try {
                    String codigoLoja = getTagValue("codigoLoja", elem);
                    String nome = getTagValue("nome", elem);

                    if (codigoLoja.isEmpty() || nome.isEmpty()) {
                        erros.add("Item " + (i + 1) + ": 'codigoLoja' e 'nome' são obrigatórios.");
                        continue;
                    }

                    skusNoXml.add(codigoLoja);

                    String grupo = getTagValue("grupo", elem);
                    if (grupo.isEmpty()) {
                        grupo = "GERAL";
                    }

                    Double precoVista = parseDoubleSafe(getTagValue("precoVista", elem), 0.0);
                    Double precoPrazo = parseDoubleSafe(getTagValue("precoPrazo", elem), 0.0);
                    String localizacao = getTagValue("localizacao", elem);
                    if (localizacao.isEmpty()) {
                        localizacao = "Depósito Central";
                    }
                    Integer quantidade = parseIntSafe(getTagValue("quantidade", elem), 0);
                    String urlImagem = getTagValue("urlImagem", elem);
                    String descricaoBreve = getTagValue("descricaoBreve", elem);

                    Produto p = new Produto(nome, grupo, precoVista, precoPrazo, codigoLoja,
                            localizacao, quantidade, urlImagem, descricaoBreve);
                    produtosParaInserirOuAtualizar.add(p);
                } catch (Exception ex) {
                    erros.add("Erro na linha " + (i + 1) + ": " + ex.getMessage());
                }
            }
        }

        // Estatísticas analíticas
        int novosInseridos = 0;
        int atualizadosPreservados = 0;
        int precosAlterados = 0;
        int nomesAlterados = 0;

        List<Produto> existentesNoBanco = produtoDAO.findAll();
        Map<String, Produto> mapaBanco = new HashMap<>();
        for (Produto p : existentesNoBanco) {
            if (p.getCodigoLoja() != null) {
                mapaBanco.put(p.getCodigoLoja(), p);
            }
        }

        for (Produto xmlP : produtosParaInserirOuAtualizar) {
            Produto doBanco = mapaBanco.get(xmlP.getCodigoLoja());
            if (doBanco != null) {
                atualizadosPreservados++;
                if (!Objects.equals(doBanco.getPrecoVista(), xmlP.getPrecoVista()) ||
                    !Objects.equals(doBanco.getPrecoPrazo(), xmlP.getPrecoPrazo())) {
                    precosAlterados++;
                }
                if (!Objects.equals(doBanco.getNome(), xmlP.getNome())) {
                    nomesAlterados++;
                }
            } else {
                novosInseridos++;
            }
        }

        int itensMantidos = 0;
        for (String skuBanco : mapaBanco.keySet()) {
            if (!skusNoXml.contains(skuBanco)) {
                itensMantidos++;
            }
        }

        int processados = 0;
        if (!produtosParaInserirOuAtualizar.isEmpty()) {
            processados = produtoDAO.batchUpsert(produtosParaInserirOuAtualizar);
        }

        LOGGER.info(String.format("Importação concluída: %d lidos, %d novos, %d atualizados.",
                nList.getLength(), novosInseridos, atualizadosPreservados));

        return new ImportResult(
                nList.getLength(),
                processados,
                novosInseridos,
                atualizadosPreservados,
                precosAlterados,
                nomesAlterados,
                itensMantidos,
                erros
        );
    }

    /**
     * Exporta o estoque atual do SQLite para um arquivo XML estruturado.
     */
    public void exportarProdutos(File destino) throws Exception {
        List<Produto> produtos = produtoDAO.findAll();

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element rootElement = doc.createElement("estoque");
        rootElement.setAttribute("sistema", "SWStock");
        rootElement.setAttribute("versao", "1.0");
        rootElement.setAttribute("dataExportacao",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        rootElement.setAttribute("totalItens", String.valueOf(produtos.size()));
        doc.appendChild(rootElement);

        for (Produto p : produtos) {
            Element prodElem = doc.createElement("produto");

            appendElement(doc, prodElem, "id", String.valueOf(p.getId() != null ? p.getId() : ""));
            appendElement(doc, prodElem, "codigoLoja", p.getCodigoLoja() != null ? p.getCodigoLoja() : "");
            appendElement(doc, prodElem, "nome", p.getNome() != null ? p.getNome() : "");
            appendElement(doc, prodElem, "grupo", p.getGrupo() != null ? p.getGrupo() : "GERAL");
            appendElement(doc, prodElem, "precoVista", String.valueOf(p.getPrecoVista() != null ? p.getPrecoVista() : 0.0));
            appendElement(doc, prodElem, "precoPrazo", String.valueOf(p.getPrecoPrazo() != null ? p.getPrecoPrazo() : 0.0));
            appendElement(doc, prodElem, "localizacao", p.getLocalizacao() != null ? p.getLocalizacao() : "");
            appendElement(doc, prodElem, "quantidade", String.valueOf(p.getQuantidade() != null ? p.getQuantidade() : 0));
            appendElement(doc, prodElem, "urlImagem", p.getUrlImagem() != null ? p.getUrlImagem() : "");
            appendElement(doc, prodElem, "descricaoBreve", p.getDescricaoBreve() != null ? p.getDescricaoBreve() : "");

            rootElement.appendChild(prodElem);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(destino), StandardCharsets.UTF_8)) {
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
        }

        LOGGER.info("Exportação para XML realizada com sucesso: " + destino.getAbsolutePath());
    }

    private void appendElement(Document doc, Element parent, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.setTextContent(textContent);
        parent.appendChild(element);
    }

    private String getTagValue(String tag, Element element) {
        NodeList nl = element.getElementsByTagName(tag);
        if (nl != null && nl.getLength() > 0) {
            Node node = nl.item(0);
            if (node != null && node.getFirstChild() != null) {
                return node.getFirstChild().getNodeValue().trim();
            }
        }
        return "";
    }

    private Double parseDoubleSafe(String val, Double def) {
        if (val == null || val.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(val.replace(",", "."));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Integer parseIntSafe(String val, Integer def) {
        if (val == null || val.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
