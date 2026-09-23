# 📦 SWStock - Sistema Desktop de Gerenciamento de Estoque

<p align="center">
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21+" />
  <img src="https://img.shields.io/badge/JavaFX-23-FF6F00?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX 23" />
  <img src="https://img.shields.io/badge/SQLite-3-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite" />
  <img src="https://img.shields.io/badge/Apache%20Maven-3.9%2B-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Plataforma" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge" alt="License MIT" />
</p>

**SWStock** é uma aplicação desktop offline moderna, rápida e modular desenvolvida para controle de estoque físico, catalogação de produtos, visualização 2D de armazém e gestão de inventário.

---

> [!IMPORTANT]
> ### 🚧 Status do Projeto: Módulo de Estoque em Desenvolvimento Ativo
> Este projeto está em constante evolução. O **módulo avançado de controle e movimentação de estoque** (levantamento detalhado, auditoria de lotes, transferências internas e relatórios analíticos) está sendo **ativamente desenvolvido e aprimorado**. Fique à vontade para acompanhar as novidades e sugerir melhorias!

---

## ✨ Funcionalidades

### 🛒 1. Catálogo de Produtos e Variações
- Cadastro, consulta e edição rápida de produtos (SKU, nomes, valores à vista/prazo e descrições).
- **Variações de Cores**: Gerenciamento de múltiplas cores por item com quantitativos dedicados.
- **Filtro Instantâneo**: Busca textual reativa por código, nome ou localização na prateleira.

### 🗺️ 2. Mapa 2D do Depósito Físico
- Representação gráfica bidimensional das estantes e corredores do depósito (Corredores A, B, C, etc.).
- Indicadores visuais de ocupação e capacidade em tempo real.
- **Filtro Interativo**: Clicar em qualquer estante isola e lista imediatamente os produtos armazenados nela.

### 👥 3. Gestão de Funcionários
- Cadastro e administração de colaboradores responsáveis pelas operações de depósito e movimentações.

### 📊 4. Levantamento e Histórico de Estoque *(Em Desenvolvimento ⚙️)*
- Registro cronológico de movimentações e auditoria de entradas e saídas.
- Interface modal para contagem física e levantamento de inventário.

### 📄 5. Relatórios em PDF & Backup XML
- **Geração de Relatórios PDF**: Exportação de listas e catálogos offline com visual limpo via OpenPDF.
- **Backup & Restauração XML**: Importação e exportação de inventário completo em formato XML estruturado com suporte a pendrives/mídias externas e validação de schema.

### 🔒 6. Arquitetura 100% Offline & Segura
- Banco de dados **SQLite embarcado** em modo WAL (*Write-Ahead Logging*), integridade referencial com chaves estrangeiras e encerramento com *graceful shutdown*.

---

## 📥 Download para Windows (.exe)

O executável portátil para Windows pode ser baixado diretamente da seção de [Releases](https://github.com/Tiago-w/swstock/releases):

1. Acesse a aba [**Releases**](https://github.com/Tiago-w/swstock/releases).
2. Baixe o arquivo **`SWStock-Windows-x64.zip`**.
3. Extraia a pasta e execute o **`SWStock.exe`**.
4. ✨ **Pronto! Não é necessário instalar o Java** (o runtime JRE 21 já vem embutido no pacote).

---

## 🛠️ Arquitetura e Estrutura do Projeto

O projeto segue o padrão arquitetural **MVC (Model-View-Controller)**:

```text
swstock/
├── pom.xml                               # Configurações do Maven, dependências e plugins de build
├── dist/
│   └── build-windows-dist.sh             # Script de automação do pacote Windows portátil
├── .github/
│   └── workflows/
│       └── build-and-release.yml         # Pipeline CI/CD (Testes + Compilação + Release)
├── src/
│   ├── main/
│   │   ├── java/com/swstock/
│   │   │   ├── Launcher.java             # Entry point para compatibilidade Fat JAR/EXE
│   │   │   ├── MainApp.java              # Ciclo de vida da aplicação JavaFX
│   │   │   ├── model/                    # Entidades de domínio (Produto, Funcionario, etc.)
│   │   │   ├── database/                 # Singleton DatabaseManager e DAOs (SQLite)
│   │   │   ├── service/                  # Regras de negócio (XML, Relatórios PDF)
│   │   │   ├── controller/               # Controladores JavaFX desacoplados
│   │   │   └── util/                     # Helpers e utilitários
│   │   └── resources/com/swstock/
│   │       ├── view/                     # Telas e modais FXML
│   │       └── css/styles.css            # Estilização visual (Design System)
│   └── test/                             # Testes automatizados (JUnit 5)
```

---

## 💻 Como Compilar e Executar Localmente

### Pré-requisitos
- **JDK 21 ou 25** instalado
- **Apache Maven 3.9+**

### 1. Clonar o Repositório
```bash
git clone git@github.com:Tiago-w/swstock.git
cd swstock
```

### 2. Rodar os Testes Automatizados
```bash
mvn clean test
```

### 3. Executar a Aplicação em Desenvolvimento
```bash
mvn javafx:run
```

### 4. Gerar o Executável Windows (.exe) e o Pacote Portátil
```bash
# Compilar e empacotar via Maven
mvn clean package

# Gerar a distribuição portátil completa (com JRE embutido e ZIP):
./dist/build-windows-dist.sh
```

---

## 🚀 CI/CD & Deploy Automatizado

Este repositório utiliza **GitHub Actions** (`.github/workflows/build-and-release.yml`).

Sempre que uma nova tag de versão for publicada:
```bash
git tag v1.0.0
git push origin v1.0.0
```
A pipeline automaticamente:
- Executa a suíte completa de testes unitários.
- Compila a aplicação e gera o executável nativo Windows `SWStock.exe`.
- Empacota o runtime Java portátil em `SWStock-Windows-x64.zip`.
- Cria a **Release no GitHub** disponibilizando o download para os usuários.

---

## 🗺️ Roadmap de Desenvolvimento

- [x] Cadastro de Produtos e Variações de Cores
- [x] Visualização 2D do Depósito Físico
- [x] Módulo de Funcionários
- [x] Exportação de Relatórios PDF e Backup XML
- [x] Empacotamento Nativo Windows (.exe) e CI/CD
- [ ] **Módulo Avançado de Controle de Estoque** *(Em andamento)*
  - [ ] Auditoria e conciliação de inventário físico vs sistêmico
  - [ ] Alertas visuais e notificações de estoque mínimo/crítico
  - [ ] Histórico detalhado com rastreabilidade por funcionário e lote
- [ ] Dashboard analítico com indicadores de giro de estoque

---

## 📄 Licença

Distribuído sob a licença **MIT**. Veja [`LICENSE`](LICENSE) para mais informações.

