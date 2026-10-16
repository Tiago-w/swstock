package com.swstock.service;

import com.swstock.database.BaseDAO;
import com.swstock.database.DatabaseManager;
import com.swstock.database.FuncionarioDAO;
import com.swstock.database.HistoricoBaseDAO;
import com.swstock.database.HistoricoEstoqueDAO;
import com.swstock.database.ProdutoCorDAO;
import com.swstock.database.ProdutoDAO;
import com.swstock.model.Base;
import com.swstock.model.Funcionario;
import com.swstock.model.HistoricoBase;
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
import java.sql.SQLException;
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
    private final BaseDAO baseDAO;
    private final HistoricoBaseDAO historicoBaseDAO;
    private final com.swstock.database.ProdutoSubcategoriaDAO produtoSubcategoriaDAO;

    public XmlService() {
        this(DatabaseManager.getInstance());
    }

    public XmlService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.produtoDAO = new ProdutoDAO(databaseManager);
        this.historicoDAO = new HistoricoEstoqueDAO(databaseManager);
        this.produtoCorDAO = new ProdutoCorDAO(databaseManager);
        this.funcionarioDAO = new FuncionarioDAO(databaseManager);
        this.baseDAO = new BaseDAO(databaseManager);
        this.historicoBaseDAO = new HistoricoBaseDAO(databaseManager);
        this.produtoSubcategoriaDAO = new com.swstock.database.ProdutoSubcategoriaDAO(databaseManager);
    }

    public XmlService(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
        this.databaseManager = DatabaseManager.getInstance();
        this.historicoDAO = new HistoricoEstoqueDAO(databaseManager);
        this.produtoCorDAO = new ProdutoCorDAO(databaseManager);
        this.funcionarioDAO = new FuncionarioDAO(databaseManager);
        this.baseDAO = new BaseDAO(databaseManager);
        this.historicoBaseDAO = new HistoricoBaseDAO(databaseManager);
        this.produtoSubcategoriaDAO = new com.swstock.database.ProdutoSubcategoriaDAO(databaseManager);
    }

    public XmlService(ProdutoDAO produtoDAO, HistoricoEstoqueDAO historicoDAO, ProdutoCorDAO produtoCorDAO, FuncionarioDAO funcionarioDAO) {
        this.produtoDAO = produtoDAO;
        this.historicoDAO = historicoDAO;
        this.produtoCorDAO = produtoCorDAO;
        this.funcionarioDAO = funcionarioDAO;
        this.databaseManager = DatabaseManager.getInstance();
        this.baseDAO = new BaseDAO(this.databaseManager);
        this.historicoBaseDAO = new HistoricoBaseDAO(this.databaseManager);
        this.produtoSubcategoriaDAO = new com.swstock.database.ProdutoSubcategoriaDAO(databaseManager);
    }

    public XmlService(ProdutoDAO produtoDAO, HistoricoEstoqueDAO historicoDAO, ProdutoCorDAO produtoCorDAO, FuncionarioDAO funcionarioDAO, BaseDAO baseDAO, HistoricoBaseDAO historicoBaseDAO) {
        this(produtoDAO, historicoDAO, produtoCorDAO, funcionarioDAO, baseDAO, historicoBaseDAO, new com.swstock.database.ProdutoSubcategoriaDAO(DatabaseManager.getInstance()));
    }

    public XmlService(ProdutoDAO produtoDAO, HistoricoEstoqueDAO historicoDAO, ProdutoCorDAO produtoCorDAO, FuncionarioDAO funcionarioDAO, BaseDAO baseDAO, HistoricoBaseDAO historicoBaseDAO, com.swstock.database.ProdutoSubcategoriaDAO produtoSubcategoriaDAO) {
        this.produtoDAO = produtoDAO;
        this.historicoDAO = historicoDAO;
        this.produtoCorDAO = produtoCorDAO;
        this.funcionarioDAO = funcionarioDAO;
        this.baseDAO = baseDAO;
        this.historicoBaseDAO = historicoBaseDAO;
        this.databaseManager = DatabaseManager.getInstance();
        this.produtoSubcategoriaDAO = produtoSubcategoriaDAO != null ? produtoSubcategoriaDAO : new com.swstock.database.ProdutoSubcategoriaDAO(this.databaseManager);
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
            int subcategoriasProcessadas,
            int movimentacoesHistoricoRestauradas,
            int funcionariosProcessados,
            int basesProcessadas,
            int movimentacoesBasesRestauradas,
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
                int coresProcessadas,
                int movimentacoesHistoricoRestauradas,
                int funcionariosProcessados,
                int basesProcessadas,
                int movimentacoesBasesRestauradas,
                List<String> erros
        ) {
            this(totalLidos, totalProcessados, novosInseridos, atualizadosPreservados,
                    precosAlterados, nomesAlterados, itensMantidosNoBancoNaoPresentesNoXml,
                    coresProcessadas, 0, movimentacoesHistoricoRestauradas, funcionariosProcessados,
                    basesProcessadas, movimentacoesBasesRestauradas, erros);
        }

        public ImportResult(
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
            this(totalLidos, totalProcessados, novosInseridos, atualizadosPreservados,
                    precosAlterados, nomesAlterados, itensMantidosNoBancoNaoPresentesNoXml,
                    coresProcessadas, 0, movimentacoesHistoricoRestauradas, funcionariosProcessados,
                    0, 0, erros);
        }

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
                    0, 0, 0, 0, 0, 0, erros);
        }

        public boolean isSucesso() {
            return erros.isEmpty() && (totalProcessados > 0 || coresProcessadas > 0 || subcategoriasProcessadas > 0 || movimentacoesHistoricoRestauradas > 0 || funcionariosProcessados > 0 || basesProcessadas > 0 || movimentacoesBasesRestauradas > 0);
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
                    String subcategoria = getTagValue("subcategoria", elem);

                    Produto p = new Produto(nome, grupo, precoVista, precoPrazo, codigoLoja,
                            localizacao, quantidade, urlImagem, descricaoBreve);
                    if (!subcategoria.isEmpty()) {
                        p.setSubcategoria(subcategoria);
                    }
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

        // 1.1 Restauracao/Sincronizacao de Subcategorias/Variacoes
        int subcategoriasProcessadas = 0;
        NodeList subsList = doc.getElementsByTagName("produto_subcategoria");
        if (subsList.getLength() == 0) {
            subsList = doc.getElementsByTagName("subcategoria");
        }
        for (int i = 0; i < subsList.getLength(); i++) {
            Node node = subsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                // Ignora se for a tag <subcategoria> dentro de um elemento <produto>
                if (elem.getParentNode() != null && "produto".equalsIgnoreCase(elem.getParentNode().getNodeName())) {
                    continue;
                }
                try {
                    String codLoja = getTagValue("produto_codigo_loja", elem);
                    Integer pIdXml = parseIntSafe(getTagValue("produto_id", elem), null);
                    String nomeSub = getTagValue("nome_subcategoria", elem);
                    if (nomeSub.isEmpty()) {
                        nomeSub = getTagValue("nome", elem);
                    }
                    Integer qtdSub = parseIntSafe(getTagValue("quantidade", elem), 0);

                    if (nomeSub.isEmpty()) continue;

                    Produto targetProd = null;
                    if (!codLoja.isEmpty()) {
                        targetProd = produtosPorCodigo.get(codLoja.trim().toUpperCase());
                    }
                    if (targetProd == null && pIdXml != null) {
                        targetProd = produtosPorId.get(pIdXml);
                    }

                    if (targetProd != null && targetProd.getId() != null) {
                        int pIdReal = targetProd.getId();
                        if (inserirSubcategoriaSeNaoExistir(pIdReal, nomeSub, qtdSub)) {
                            subcategoriasProcessadas++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar subcategoria do XML: " + ex.getMessage());
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
                    String numFunc = getTagValue("numero", elem);
                    if (!nomeFunc.isEmpty()) {
                        if (inserirFuncionarioSeNaoExistir(nomeFunc, numFunc)) {
                            funcionariosProcessados++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar funcionario do XML: " + ex.getMessage());
                }
            }
        }

        // 4. Restauracao/Sincronizacao de Bases de Tintas
        int basesProcessadas = 0;
        NodeList basesList = doc.getElementsByTagName("base");
        for (int i = 0; i < basesList.getLength(); i++) {
            Node node = basesList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                try {
                    String tamanho = getTagValue("tamanho", elem);
                    if (tamanho.isEmpty()) {
                        tamanho = getTagValue("codigo", elem);
                    }
                    String nome = getTagValue("nome", elem);
                    if (tamanho.isEmpty() || nome.isEmpty()) continue;

                    String tipo = getTagValue("tipo", elem);
                    if (tipo.isEmpty()) tipo = "AUTOMOTIVA";
                    String fabricante = getTagValue("fabricante", elem);
                    if (fabricante.isEmpty()) fabricante = "Geral";
                    Double valorCompra = parseDoubleSafe(getTagValue("valor_compra", elem), 0.0);
                    Integer quantidade = parseIntSafe(getTagValue("quantidade", elem), 0);

                    if (sincronizarBaseComPreservacaoEstoque(tamanho, nome, tipo, fabricante, quantidade, valorCompra)) {
                        basesProcessadas++;
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar base do XML: " + ex.getMessage());
                }
            }
        }

        // Mapa de bases para correlacionar movimentacoes
        Map<String, Base> basesPorChave = new HashMap<>();
        Map<Integer, Base> basesPorId = new HashMap<>();
        try {
            for (Base b : baseDAO.findAll()) {
                String tam = b.getTamanho() != null ? b.getTamanho().trim().toUpperCase() : "";
                String nom = b.getNome() != null ? b.getNome().trim().toUpperCase() : "";
                basesPorChave.put(tam + "|" + nom, b);
                if (b.getTamanho() != null) {
                    basesPorChave.put(b.getTamanho().trim().toUpperCase(), b);
                }
                if (b.getId() != null) {
                    basesPorId.put(b.getId(), b);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Erro ao carregar mapa de bases: " + ex.getMessage());
        }

        // 5. Restauracao de Historico de Movimentacoes de Bases
        int movimentacoesBasesRestauradas = 0;
        NodeList histBasesList = doc.getElementsByTagName("movimentacao_base");
        if (histBasesList.getLength() == 0) {
            histBasesList = doc.getElementsByTagName("historico_base");
        }
        for (int i = 0; i < histBasesList.getLength(); i++) {
            Node node = histBasesList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                try {
                    String codBase = getTagValue("base_tamanho", elem);
                    if (codBase.isEmpty()) {
                        codBase = getTagValue("base_codigo", elem);
                    }
                    Integer bIdXml = parseIntSafe(getTagValue("base_id", elem), null);
                    String dataHora = getTagValue("data_hora", elem);
                    String tipo = getTagValue("tipo", elem);
                    Integer qtdAlt = parseIntSafe(getTagValue("quantidade_alterada", elem), 0);
                    Integer qtdAnt = parseIntSafe(getTagValue("quantidade_anterior", elem), 0);
                    Integer qtdNov = parseIntSafe(getTagValue("quantidade_nova", elem), 0);
                    String motivo = getTagValue("motivo", elem);
                    String responsavel = getTagValue("responsavel", elem);

                    if (tipo.isEmpty()) tipo = qtdAlt >= 0 ? "ENTRADA" : "SAIDA";
                    if (responsavel.isEmpty()) responsavel = "Nao informado";

                    Base targetBase = null;
                    if (!codBase.isEmpty()) {
                        targetBase = basesPorChave.get(codBase.trim().toUpperCase());
                    }
                    if (targetBase == null && bIdXml != null) {
                        targetBase = basesPorId.get(bIdXml);
                    }

                    if (targetBase != null && targetBase.getId() != null) {
                        int bIdReal = targetBase.getId();
                        if (inserirHistoricoBaseSeNaoExistir(bIdReal, dataHora, tipo, qtdAlt, qtdAnt, qtdNov, motivo, responsavel)) {
                            movimentacoesBasesRestauradas++;
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao processar historico de base do XML: " + ex.getMessage());
                }
            }
        }

        LOGGER.info(String.format("Importacao/Backup concluida: %d produtos lidos, %d novos, %d atualizados, %d cores, %d subcategorias, %d movimentacoes, %d funcionarios, %d bases, %d mov_bases.",
                nList.getLength(), novosInseridos, atualizadosPreservados, coresProcessadas, subcategoriasProcessadas, historicoRestaurado, funcionariosProcessados, basesProcessadas, movimentacoesBasesRestauradas));

        return new ImportResult(
                nList.getLength(),
                processados,
                novosInseridos,
                atualizadosPreservados,
                precosAlterados,
                nomesAlterados,
                itensMantidos,
                coresProcessadas,
                subcategoriasProcessadas,
                historicoRestaurado,
                funcionariosProcessados,
                basesProcessadas,
                movimentacoesBasesRestauradas,
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
            appendElement(doc, prodElem, "subcategoria", p.getSubcategoria() != null ? p.getSubcategoria() : "");

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

        // 2.1 Secao de Subcategorias/Variacoes dos Produtos
        Element subsElem = doc.createElement("produto_subcategorias");
        rootElement.appendChild(subsElem);

        try {
            Map<Integer, List<com.swstock.model.ProdutoSubcategoria>> mapaSubs = produtoSubcategoriaDAO.getAllSubcategoriasAgrupadas();
            for (Map.Entry<Integer, List<com.swstock.model.ProdutoSubcategoria>> entry : mapaSubs.entrySet()) {
                int pId = entry.getKey();
                String pCod = mapaIdParaCodigo.getOrDefault(pId, "");
                for (com.swstock.model.ProdutoSubcategoria sub : entry.getValue()) {
                    Element subElem = doc.createElement("produto_subcategoria");
                    appendElement(doc, subElem, "id", String.valueOf(sub.getId() != null ? sub.getId() : ""));
                    appendElement(doc, subElem, "produto_id", String.valueOf(pId));
                    appendElement(doc, subElem, "produto_codigo_loja", pCod);
                    appendElement(doc, subElem, "nome_subcategoria", sub.getNomeSubcategoria() != null ? sub.getNomeSubcategoria() : "");
                    appendElement(doc, subElem, "quantidade", String.valueOf(sub.getQuantidade()));
                    subsElem.appendChild(subElem);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar subcategorias para XML: " + ex.getMessage(), ex);
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
                appendElement(doc, fElem, "numero", f.getNumero() != null ? f.getNumero() : "");
                funcElem.appendChild(fElem);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar funcionarios para XML: " + ex.getMessage(), ex);
        }

        // 5. Secao de Bases de Tintas (Automotivas e Prediais)
        Element basesElem = doc.createElement("bases");
        rootElement.appendChild(basesElem);

        try {
            List<Base> todasBases = baseDAO.findAll();
            for (Base b : todasBases) {
                Element bElem = doc.createElement("base");
                appendElement(doc, bElem, "id", String.valueOf(b.getId() != null ? b.getId() : ""));
                appendElement(doc, bElem, "tamanho", b.getTamanho() != null ? b.getTamanho() : "");
                appendElement(doc, bElem, "codigo", b.getTamanho() != null ? b.getTamanho() : "");
                appendElement(doc, bElem, "nome", b.getNome() != null ? b.getNome() : "");
                appendElement(doc, bElem, "tipo", b.getTipo() != null ? b.getTipo() : "AUTOMOTIVA");
                appendElement(doc, bElem, "fabricante", b.getFabricante() != null ? b.getFabricante() : "Geral");
                appendElement(doc, bElem, "quantidade", String.valueOf(b.getQuantidade() != null ? b.getQuantidade() : 0));
                appendElement(doc, bElem, "valor_compra", String.valueOf(b.getValorCompra() != null ? b.getValorCompra() : 0.0));
                basesElem.appendChild(bElem);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar bases para XML: " + ex.getMessage(), ex);
        }

        // 6. Secao de Historico de Movimentacoes de Bases
        Element histBasesElem = doc.createElement("historico_bases");
        rootElement.appendChild(histBasesElem);

        try {
            List<HistoricoBase> todosHistoricoBases = historicoBaseDAO.findAllGlobal(null, null, null, null, null);
            for (HistoricoBase hb : todosHistoricoBases) {
                Element hbElem = doc.createElement("movimentacao_base");
                appendElement(doc, hbElem, "id", String.valueOf(hb.getId() != null ? hb.getId() : ""));
                appendElement(doc, hbElem, "base_id", String.valueOf(hb.getBaseId() != null ? hb.getBaseId() : ""));
                String tamHb = hb.getBaseTamanho() != null ? hb.getBaseTamanho() : (hb.getBaseCodigo() != null ? hb.getBaseCodigo() : "");
                appendElement(doc, hbElem, "base_tamanho", tamHb);
                appendElement(doc, hbElem, "base_codigo", tamHb);
                appendElement(doc, hbElem, "data_hora", hb.getDataHora() != null ? hb.getDataHora() : "");
                appendElement(doc, hbElem, "tipo", hb.getTipo() != null ? hb.getTipo() : "AJUSTE");
                appendElement(doc, hbElem, "quantidade_alterada", String.valueOf(hb.getQuantidadeAlterada() != null ? hb.getQuantidadeAlterada() : 0));
                appendElement(doc, hbElem, "quantidade_anterior", String.valueOf(hb.getQuantidadeAnterior() != null ? hb.getQuantidadeAnterior() : 0));
                appendElement(doc, hbElem, "quantidade_nova", String.valueOf(hb.getQuantidadeNova() != null ? hb.getQuantidadeNova() : 0));
                appendElement(doc, hbElem, "motivo", hb.getMotivo() != null ? hb.getMotivo() : "");
                appendElement(doc, hbElem, "responsavel", hb.getResponsavel() != null ? hb.getResponsavel() : "Nao informado");
                histBasesElem.appendChild(hbElem);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso ao exportar historico de bases para XML: " + ex.getMessage(), ex);
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

    private boolean inserirSubcategoriaSeNaoExistir(int produtoId, String nomeSub, int quantidade) {
        String nomeLimpo = nomeSub.trim().toUpperCase();
        String sqlCheck = "SELECT id FROM produto_subcategorias WHERE produto_id = ? AND UPPER(nome_subcategoria) = ?";
        String sqlInsert = "INSERT INTO produto_subcategorias (produto_id, nome_subcategoria, quantidade) VALUES (?, ?, ?)";

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
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erro ao inserir subcategoria via XML: " + nomeSub, e);
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

    private boolean inserirFuncionarioSeNaoExistir(String nomeFuncionario, String numero) {
        String sqlCheck = "SELECT id, numero FROM funcionarios WHERE UPPER(TRIM(nome)) = ?;";
        String sqlInsert = "INSERT INTO funcionarios (nome, numero) VALUES (?, ?);";
        String sqlUpdate = "UPDATE funcionarios SET numero = ? WHERE id = ?;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, nomeFuncionario.trim().toUpperCase());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String numAtual = rs.getString("numero");
                    if ((numAtual == null || numAtual.trim().isEmpty()) && numero != null && !numero.trim().isEmpty()) {
                        try (PreparedStatement updateStmt = conn.prepareStatement(sqlUpdate)) {
                            updateStmt.setString(1, numero.trim());
                            updateStmt.setInt(2, id);
                            updateStmt.executeUpdate();
                        }
                    }
                    return false;
                }
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                insertStmt.setString(1, nomeFuncionario.trim());
                insertStmt.setString(2, numero != null ? numero.trim() : "");
                return insertStmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao inserir funcionario do backup: " + nomeFuncionario, e);
            return false;
        }
    }

    private boolean sincronizarBaseComPreservacaoEstoque(String tamanho, String nome, String tipo, String fabricante, int quantidade, double valorCompra) {
        String tamLimpo = tamanho.trim();
        String nomeLimpo = nome.trim();
        String tipoLimpo = tipo.trim().toUpperCase();
        String fabLimpo = fabricante.trim();

        String sqlCheck = "SELECT id, quantidade FROM bases WHERE UPPER(tamanho) = ? AND UPPER(nome) = ? AND UPPER(tipo) = ? AND UPPER(fabricante) = ?;";
        String sqlInsert = "INSERT INTO bases (tamanho, nome, tipo, fabricante, quantidade, valor_compra) VALUES (?, ?, ?, ?, ?, ?);";
        String sqlUpdatePreservando = "UPDATE bases SET valor_compra = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, tamLimpo.toUpperCase());
            checkStmt.setString(2, nomeLimpo.toUpperCase());
            checkStmt.setString(3, tipoLimpo);
            checkStmt.setString(4, fabLimpo.toUpperCase());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Ja existe: atualiza dados cadastrais, MAS PRESERVA o estoque fisico existente
                    int existingId = rs.getInt("id");
                    try (PreparedStatement updateStmt = conn.prepareStatement(sqlUpdatePreservando)) {
                        updateStmt.setDouble(1, valorCompra);
                        updateStmt.setInt(2, existingId);
                        return updateStmt.executeUpdate() > 0;
                    }
                }
            }

            // Nao existe: insere nova base com a quantidade inicial do XML
            try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                insertStmt.setString(1, tamLimpo);
                insertStmt.setString(2, nomeLimpo);
                insertStmt.setString(3, tipoLimpo);
                insertStmt.setString(4, fabLimpo);
                insertStmt.setInt(5, Math.max(0, quantidade));
                insertStmt.setDouble(6, Math.max(0.0, valorCompra));
                return insertStmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao sincronizar base do XML: " + tamanho + " - " + nome, e);
            return false;
        }
    }

    private boolean inserirHistoricoBaseSeNaoExistir(int baseId, String dataHora, String tipo,
                                                     int qtdAlt, int qtdAnt, int qtdNov, String motivo, String responsavel) {
        String sqlCheck = """
            SELECT id FROM historico_bases
            WHERE base_id = ? AND data_hora = ? AND tipo = ? AND quantidade_alterada = ? AND quantidade_nova = ?
            LIMIT 1;
        """;
        String sqlInsert = """
            INSERT INTO historico_bases (base_id, data_hora, tipo, quantidade_alterada, quantidade_anterior, quantidade_nova, motivo, responsavel)
            VALUES (?, COALESCE(?, datetime('now', 'localtime')), ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setInt(1, baseId);
            checkStmt.setString(2, dataHora != null ? dataHora : "");
            checkStmt.setString(3, tipo);
            checkStmt.setInt(4, qtdAlt);
            checkStmt.setInt(5, qtdNov);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return false; // Movimentacao de base ja registrada
                }
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                insertStmt.setInt(1, baseId);
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
            LOGGER.log(Level.WARNING, "Erro ao restaurar historico de base para baseId=" + baseId, e);
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
