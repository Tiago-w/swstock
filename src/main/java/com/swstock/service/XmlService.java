package com.swstock.service;

import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Funcionario;
import com.swstock.model.HistoricoEstoque;
import com.swstock.model.Produto;
import com.swstock.model.ProdutoCor;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servico de Importacao e Exportacao de Produtos via arquivos XML.
 * Realiza backup completo 360 graus (Produtos, Cores/Variacoes, Historico de Movimentacoes, Funcionarios)
 * e sincronizacao inteligente com preservacao absoluta de estoque fisico.
 */
public class XmlService {

    private static final Logger LOGGER = Logger.getLogger(XmlService.class.getName());

    private final DatabaseManager databaseManager;
    private final ProdutoDAO produtoDAO;
    private final HistoricoEstoqueDAO historicoDAO;
    private final ProdutoCorDAO produtoCorDAO;
    private final FuncionarioDAO funcionarioDAO;

    public XmlService() {
        this(DatabaseManager.getInstance());
    }

    public XmlService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.produtoDAO = new ProdutoDAO(databaseManager);
        this.historicoDAO = new HistoricoEstoqueDAO(databaseManager);
        this.produtoCorDAO = new ProdutoCorDAO(databaseManager);
        this.funcionarioDAO = new FuncionarioDAO(databaseManager);
    }

    public XmlService(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
        this.databaseManager = DatabaseManager.getInstance();
        this.historicoDAO = new HistoricoEstoqueDAO(databaseManager);
        this.produtoCorDAO = new ProdutoCorDAO(databaseManager);
        this.funcionarioDAO = new FuncionarioDAO(databaseManager);
    }

    public XmlService(ProdutoDAO produtoDAO, HistoricoEstoqueDAO historicoDAO, ProdutoCorDAO produtoCorDAO, FuncionarioDAO funcionarioDAO) {
        this.produtoDAO = produtoDAO;
        this.historicoDAO = historicoDAO;
        this.produtoCorDAO = produtoCorDAO;
        this.funcionarioDAO = funcionarioDAO;
        this.databaseManager = DatabaseManager.getInstance();
    }

    /**
     * Resultado enriquecido da operacao de importacao de XML.
     */
    public record ImportResult(
            int totalLidos,
            int totalProcessados,
            int novosInseridos,
            int atualizadosPreservados,
            int precosAlterados,
            int nomesAlterados,
            int itensMantidosNoBancoNaoPresentesNoXml,
            int coresProcessadas,
            int movimentacoesHistoricoRestauradas,
            int funcionariosProcessados,
            List<String> erros
    ) {
        public ImportResult(
                int totalLidos,
                int totalProcessados,
                int novosInseridos,
                int atualizadosPreservados,
                int precosAlterados,
                int nomesAlterados,
                int itensMantidosNoBancoNaoPresentesNoXml,
                List<String> erros
        ) {
            this(totalLidos, totalProcessados, novosInseridos, atualizadosPreservados,
                    precosAlterados, nomesAlterados, itensMantidosNoBancoNaoPresentesNoXml,
                    0, 0, 0, erros);
        }

        public boolean isSucesso() {
            return erros.isEmpty() && (totalProcessados > 0 || coresProcessadas > 0 || movimentacoesHistoricoRestauradas > 0 || funcionariosProcessados > 0);
        }
    }

    /**
     * Importa produtos de um arquivo XML com sincronizacao inteligente e restauracao completa.
     */
    public ImportResult importarProdutos(File xmlFile) throws Exception {
        if (xmlFile == null || !xmlFile.exists() || !xmlFile.canRead()) {
            throw new IllegalArgumentException("Arquivo XML invalido ou inacessivel.");
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
                        erros.add("Item " + (i + 1) + ": 'codigoLoja' e 'nome' sao obrigatorios.");
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
                        localizacao = "Deposito Central";
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

        // Estatisticas analiticas de produtos
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

        // Atualiza mapa de produtos em memoria com os IDs reais do banco de dados
        List<Produto> todosProdutosAposUpsert = produtoDAO.findAll();
        Map<String, Produto> produtosPorCodigo = new HashMap<>();
        Map<Integer, Produto> produtosPorId = new HashMap<>();
        for (Produto p : todosProdutosAposUpsert) {
            if (p.getCodigoLoja() != null) {
                produtosPorCodigo.put(p.getCodigoLoja().trim().toUpperCase(), p);
            }
            if (p.getId() != null) {
                produtosPorId.put(p.getId(), p);
            }
        }

        // 1. Restauracao/Sincronizacao de Cores/Variacoes
        int coresProcessadas = 0;
        NodeList coresList = doc.getElementsByTagName("cor");
        if (coresList.getLength() == 0) {
            coresList = doc.getElementsByTagName("produto_cor");
        }
        for (int i = 0; i < coresList.getLength(); i++) {
            Node node = coresList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                try {
                    String codLoja = getTagValue("produto_codigo_loja", elem);
                    Integer pIdXml = parseIntSafe(getTagValue("produto_id", elem), null);
                    String nomeCor = getTagValue("nome_cor", elem);
                    Integer qtdCor = parseIntSafe(getTagValue("quantidade", elem), 0);

                    if (nomeCor.isEmpty()) continue;

                    Produto targetProd = null;
                    if (!codLoja.isEmpty()) {
                        targetProd = produtosPorCodigo.get(codLoja.trim().toUpperCase());
                    }
                    if (targetProd == null && pIdXml != null) {
                        targetProd = produtosPorId.get(pIdXml);
                    }

                    if (targetProd != null && targetProd.getId() != null) {
                        int pIdReal = targetProd.getId();
                        if (inserirCorSeNaoExistir(pIdReal, nomeCor, qtdCor)) {
                            coresProcessadas++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar cor do XML: " + ex.getMessage());
                }
            }
        }

        // 2. Restauracao de Historico de Movimentacoes
        int historicoRestaurado = 0;
        NodeList histList = doc.getElementsByTagName("movimentacao");
        if (histList.getLength() == 0) {
            histList = doc.getElementsByTagName("historico");
        }
        for (int i = 0; i < histList.getLength(); i++) {
            Node node = histList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                try {
                    String codLoja = getTagValue("produto_codigo_loja", elem);
                    Integer pIdXml = parseIntSafe(getTagValue("produto_id", elem), null);
                    String dataHora = getTagValue("data_hora", elem);
                    String tipo = getTagValue("tipo", elem);
                    Integer qtdAlt = parseIntSafe(getTagValue("quantidade_alterada", elem), 0);
                    Integer qtdAnt = parseIntSafe(getTagValue("quantidade_anterior", elem), 0);
                    Integer qtdNov = parseIntSafe(getTagValue("quantidade_nova", elem), 0);
                    String motivo = getTagValue("motivo", elem);
                    String responsavel = getTagValue("responsavel", elem);

                    if (tipo.isEmpty()) tipo = qtdAlt >= 0 ? "ENTRADA" : "SAIDA";
                    if (responsavel.isEmpty()) responsavel = "Nao informado";

                    Produto targetProd = null;
                    if (!codLoja.isEmpty()) {
                        targetProd = produtosPorCodigo.get(codLoja.trim().toUpperCase());
                    }
                    if (targetProd == null && pIdXml != null) {
                        targetProd = produtosPorId.get(pIdXml);
                    }

                    if (targetProd != null && targetProd.getId() != null) {
                        int pIdReal = targetProd.getId();
                        if (inserirHistoricoSeNaoExistir(pIdReal, dataHora, tipo, qtdAlt, qtdAnt, qtdNov, motivo, responsavel)) {
                            historicoRestaurado++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar historico do XML: " + ex.getMessage());
                }
            }
        }

        // 3. Restauracao/Sincronizacao de Funcionarios
        int funcionariosProcessados = 0;
        NodeList funcList = doc.getElementsByTagName("funcionario");
        for (int i = 0; i < funcList.getLength(); i++) {
            Node node = funcList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                try {
                    String nomeFunc = getTagValue("nome", elem);
                    if (!nomeFunc.isEmpty()) {
                        if (inserirFuncionarioSeNaoExistir(nomeFunc)) {
                            funcionariosProcessados++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar funcionario do XML: " + ex.getMessage());
                }
            }
        }

        LOGGER.info(String.format("Importacao/Backup concluida: %d produtos lidos, %d novos, %d atualizados, %d cores, %d movimentacoes, %d funcionarios.",
                nList.getLength(), novosInseridos, atualizadosPreservados, coresProcessadas, historicoRestaurado, funcionariosProcessados));

        return new ImportResult(
                nList.getLength(),
                processados,
                novosInseridos,
                atualizadosPreservados,
                precosAlterados,
                nomesAlterados,
                itensMantidos,
                coresProcessadas,
                historicoRestaurado,
                funcionariosProcessados,
                erros
        );
    }

    /**
     * Exporta o estoque completo 360 graus do SQLite para um arquivo XML estruturado.
     * Salva absolutamente tudo: produtos, cores/variacoes, historico de movimentacoes e funcionarios.
     */
    public void exportarProdutos(File destino) throws Exception {
        List<Produto> produtos = produtoDAO.findAll();
        Map<Integer, String> mapaIdParaCodigo = new HashMap<>();
        for (Produto p : produtos) {
            if (p.getId() != null && p.getCodigoLoja() != null) {
                mapaIdParaCodigo.put(p.getId(), p.getCodigoLoja());
            }
        }

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element rootElement = doc.createElement("swstock_backup");
        rootElement.setAttribute("sistema", "SWStock");
        rootElement.setAttribute("versao", "2.0");
        rootElement.setAttribute("dataExportacao",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        rootElement.setAttribute("totalProdutos", String.valueOf(produtos.size()));
        doc.appendChild(rootElement);

        // 1. Secao de Produtos
        Element produtosElem = doc.createElement("produtos");
        rootElement.appendChild(produtosElem);

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

            produtosElem.appendChild(prodElem);
        }

        // 2. Secao de Cores/Variacoes dos Produtos
        Element coresElem = doc.createElement("produto_cores");
        rootElement.appendChild(coresElem);

        try {
            Map<Integer, List<ProdutoCor>> mapaCores = produtoCorDAO.getAllCoresAgrupadas();
            for (Map.Entry<Integer, List<ProdutoCor>> entry : mapaCores.entrySet()) {
                int pId = entry.getKey();
                String pCod = mapaIdParaCodigo.getOrDefault(pId, "");
                for (ProdutoCor cor : entry.getValue()) {
                    Element corElem = doc.createElement("cor");
                    appendElement(doc, corElem, "id", String.valueOf(cor.getId() != null ? cor.getId() : ""));
                    appendElement(doc, corElem, "produto_id", String.valueOf(pId));
                    appendElement(doc, corElem, "produto_codigo_loja", pCod);
                    appendElement(doc, corElem, "nome_cor", cor.getNomeCor() != null ? cor.getNomeCor() : "");
                    appendElement(doc, corElem, "quantidade", String.valueOf(cor.getQuantidade() != null ? cor.getQuantidade() : 0));
                    coresElem.appendChild(corElem);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar cores para XML: " + ex.getMessage(), ex);
        }

        // 3. Secao de Historico Completo de Movimentacoes
        Element historicoElem = doc.createElement("historico_estoque");
        rootElement.appendChild(historicoElem);

        try {
            List<HistoricoEstoque> todasMovimentacoes = historicoDAO.findAllGlobal(null, null, null, null, null);
            for (HistoricoEstoque h : todasMovimentacoes) {
                Element movElem = doc.createElement("movimentacao");
                appendElement(doc, movElem, "id", String.valueOf(h.getId() != null ? h.getId() : ""));
                appendElement(doc, movElem, "produto_id", String.valueOf(h.getProdutoId() != null ? h.getProdutoId() : ""));
                appendElement(doc, movElem, "produto_codigo_loja", h.getProdutoCodigo() != null ? h.getProdutoCodigo() : "");
                appendElement(doc, movElem, "data_hora", h.getDataHora() != null ? h.getDataHora() : "");
                appendElement(doc, movElem, "tipo", h.getTipo() != null ? h.getTipo() : "AJUSTE");
                appendElement(doc, movElem, "quantidade_alterada", String.valueOf(h.getQuantidadeAlterada() != null ? h.getQuantidadeAlterada() : 0));
                appendElement(doc, movElem, "quantidade_anterior", String.valueOf(h.getQuantidadeAnterior() != null ? h.getQuantidadeAnterior() : 0));
                appendElement(doc, movElem, "quantidade_nova", String.valueOf(h.getQuantidadeNova() != null ? h.getQuantidadeNova() : 0));
                appendElement(doc, movElem, "motivo", h.getMotivo() != null ? h.getMotivo() : "");
                appendElement(doc, movElem, "responsavel", h.getResponsavel() != null ? h.getResponsavel() : "Nao informado");

                historicoElem.appendChild(movElem);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar historico para XML: " + ex.getMessage(), ex);
        }

        // 4. Secao de Funcionarios Cadastrados
        Element funcElem = doc.createElement("funcionarios");
        rootElement.appendChild(funcElem);

        try {
            List<Funcionario> funcionarios = funcionarioDAO.findAll();
            for (Funcionario f : funcionarios) {
                Element fElem = doc.createElement("funcionario");
                appendElement(doc, fElem, "id", String.valueOf(f.getId() != null ? f.getId() : ""));
                appendElement(doc, fElem, "nome", f.getNome() != null ? f.getNome() : "");
                funcElem.appendChild(fElem);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar funcionarios para XML: " + ex.getMessage(), ex);
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

        LOGGER.info("Backup completo exportado para XML com sucesso: " + destino.getAbsolutePath());
    }

    private boolean inserirCorSeNaoExistir(int produtoId, String nomeCor, int quantidade) {
        String nomeLimpo = nomeCor.trim().toUpperCase();
        String sqlCheck = "SELECT id FROM produto_cores WHERE produto_id = ? AND UPPER(nome_cor) = ?";
        String sqlInsert = "INSERT INTO produto_cores (produto_id, nome_cor, quantidade) VALUES (?, ?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setInt(1, produtoId);
            checkStmt.setString(2, nomeLimpo);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return false; // Ja existe, mantem
                }
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                insertStmt.setInt(1, produtoId);
                insertStmt.setString(2, nomeLimpo);
                insertStmt.setInt(3, Math.max(0, quantidade));
                return insertStmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao inserir cor do backup: " + nomeCor, e);
            return false;
        }
    }

    private boolean inserirHistoricoSeNaoExistir(int produtoId, String dataHora, String tipo,
                                                  int qtdAlt, int qtdAnt, int qtdNov, String motivo, String responsavel) {
        String sqlCheck = """
            SELECT id FROM historico_estoque
            WHERE produto_id = ? AND data_hora = ? AND tipo = ? AND quantidade_alterada = ? AND quantidade_nova = ?
            LIMIT 1;
        """;
        String sqlInsert = """
            INSERT INTO historico_estoque (produto_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel)
            VALUES (?, COALESCE(?, datetime('now', 'localtime')), ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setInt(1, produtoId);
            checkStmt.setString(2, dataHora != null ? dataHora : "");
            checkStmt.setString(3, tipo);
            checkStmt.setInt(4, qtdAlt);
            checkStmt.setInt(5, qtdNov);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return false; // Movimentacao ja registrada, evita duplicacao
                }
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                insertStmt.setInt(1, produtoId);
                insertStmt.setString(2, (dataHora != null && !dataHora.isEmpty()) ? dataHora : null);
                insertStmt.setString(3, tipo);
                insertStmt.setInt(4, qtdAlt);
                insertStmt.setInt(5, qtdAnt);
                insertStmt.setInt(6, qtdNov);
                insertStmt.setString(7, motivo);
                insertStmt.setString(8, responsavel);
                return insertStmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao restaurar historico do backup para produtoId=" + produtoId, e);
            return false;
        }
    }

    private boolean inserirFuncionarioSeNaoExistir(String nomeFuncionario) {
        String sql = "INSERT OR IGNORE INTO funcionarios (nome) VALUES (?);";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nomeFuncionario.trim());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao inserir funcionario do backup: " + nomeFuncionario, e);
            return false;
        }
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
