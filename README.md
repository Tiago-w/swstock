# SWStock - Sistema Desktop Offline de Gerenciamento de Estoque

**SWStock** é uma aplicação desktop offline moderna, robusta e modular desenvolvida em **Java 25**, **JavaFX**, **SQLite (JDBC)** e estruturada sob o padrão arquitetural **MVC (Model-View-Controller)**.

---

## 🛠 Stack Tecnológica

- **Linguagem**: Java 25
- **Interface Gráfica**: JavaFX 23 (BorderPane, FXML desacoplados, CSS customizado)
- **Banco de Dados**: SQLite offline com driver oficial `sqlite-jdbc` (WAL mode, Foreign Keys)
- **Build & Gerenciamento**: Apache Maven 3.9+
- **Testes**: JUnit 5

---

## 📁 Estrutura de Diretórios (MVC)

```text
swstock/
├── pom.xml
├── README.md
├── sample_data/
│   └── produtos_exemplo.xml               # Arquivo XML de teste para importação via Pendrive
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── swstock/
│   │   │           ├── MainApp.java       # Ponto de entrada JavaFX e hook de encerramento seguro
│   │   │           ├── Launcher.java      # Wrapper de execução
│   │   │           ├── model/
│   │   │           │   └── Produto.java   # Entidade de domínio (POJO / JavaFX ready)
│   │   │           ├── database/
│   │   │           │   ├── DatabaseManager.java # Singleton SQLite, DDL, WAL e safe close
│   │   │           │   └── ProdutoDAO.java      # CRUD, UPSERT atômico e filtros
│   │   │           ├── service/
│   │   │           │   └── XmlService.java      # Importação/Exportação XML estruturado
│   │   │           └── controller/
│   │   │               ├── MainController.java  # Controlador da janela principal e tabela
│   │   │               ├── Map2DController.java # Layout físico 2D do depósito e filtro visual
│   │   │               └── ProductDetailController.java # Modal de detalhes, auto-save e pipeline IA
│   │   └── resources/
│   │       └── com/
│   │           └── swstock/
│   │               ├── view/
│   │               │   ├── MainView.fxml        # Layout principal com Banner e Sidebar retrátil
│   │               │   ├── Map2DView.fxml       # Grid 2D interativo das estantes
│   │               │   └── ProductDetailModal.fxml # Modal com stepper +/- e área de IA
│   │               └── css/
│   │                   └── styles.css           # Design System moderno em azul vibrante
│   └── test/
│       └── java/
│           └── com/
│               └── swstock/
│                   ├── ProdutoDAOTest.java      # Testes de persistência, filtros e UPSERT
│                   └── XmlServiceTest.java      # Testes de importação e exportação XML
```

---

## 🚀 Funcionalidades Principais

### 1. Banner Superior & Menu Retrátil
- **Banner Vibrante**: Fundo em degradê azul (#1565C0 / #0D47A1), tipografia estilizada e contador geral de itens.
- **Menu Sanduíche (☰)**: Botão de alternância que expande e recolhe o painel lateral com animação responsiva.

### 2. Mapa 2D do Depósito Físico
- Renderiza graficamente as estantes do armazém (Corredores A, B, C e setores customizados).
- Mostra indicadores de ocupação (unidades armazenadas e status Livre/Ocupada).
- Clicar em qualquer estante filtra instantaneamente os produtos correspondentes na tabela principal.

### 3. Importação e Exportação XML
- **Importação**: Aciona `FileChooser` otimizado para pendrives e mídias removíveis, executa validação e realiza **UPSERT** atômico no SQLite.
- **Exportação**: Gera arquivo XML estruturado contendo o inventário completo com metadados e data de exportação.

### 4. Tabela Principal Reativa
- Virtualização e paginação nativa de alta performance.
- Colunas: *Código da Loja*, *Nome*, *Valor à Vista (R$)*, *Valor a Prazo (R$)*, *Localização*, *Quantidade (Badges de status)* e *Ações*.
- Busca textual instantânea por nome, SKU ou descrição.

### 5. Modal de Detalhes & Preparação para IA
- **Edição Rápida de Estoque**: Botões `[ + ]` e `[ - ]` com auto-save em tempo real no SQLite e feedback visual imediato.
- **Preparação para IA**: Placeholder para injeção de fotos de produtos e área de texto integrada para síntese de descrições comerciais geradas por agentes automatizados.

### 6. Encerramento Seguro
- Botão "Encerrar & Fechar DB" e handlers de fechamento de janela que garantem o `commit()` de transações ativas e a chamada segura de `.close()` no driver SQLite.

---

## 📦 Como Compilar e Executar

### Pré-requisitos
- JDK 25 (ou JDK 21+ LTS)
- Apache Maven 3.9+

### 1. Executar os Testes Unitários
```bash
mvn clean test
```

### 2. Executar a Aplicação via JavaFX Plugin
```bash
mvn javafx:run
```

### 3. Executar via Launcher (Maven Exec)
```bash
mvn compile exec:java
```

### 4. Gerar o Pacote JAR
```bash
mvn clean package
```
