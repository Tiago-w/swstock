#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"
WIN_PKG_DIR="${DIST_DIR}/swstock-windows"
JRE_ZIP_TMP="/tmp/temurin21_jre_windows_x64.zip"

echo "=== Iniciando empacotamento do SWStock para Windows ==="

cd "${ROOT_DIR}"

# 1. Compilar e empacotar via Maven
echo "[1/5] Compilando e gerando SWStock.exe via Maven..."
mvn clean package -DskipTests

# 2. Criar diretório de distribuição
echo "[2/5] Estruturando pasta de distribuição..."
rm -rf "${WIN_PKG_DIR}"
mkdir -p "${WIN_PKG_DIR}"

cp "${ROOT_DIR}/target/SWStock.exe" "${WIN_PKG_DIR}/"
cp "${ROOT_DIR}/target/swstock.jar" "${WIN_PKG_DIR}/"

# 3. Baixar e configurar o JRE Windows x64 (Temurin 21) se ainda não baixado
echo "[3/5] Baixando JRE Windows x64 (Eclipse Temurin 21)..."
if [ ! -f "${JRE_ZIP_TMP}" ]; then
    curl -L "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse" -o "${JRE_ZIP_TMP}"
fi

echo "[3/5] Extraindo JRE para a pasta de distribuição..."
TMP_EXTRACT_DIR="$(mktemp -d)"
unzip -q "${JRE_ZIP_TMP}" -d "${TMP_EXTRACT_DIR}"
JRE_FOLDER_NAME="$(ls "${TMP_EXTRACT_DIR}")"
mv "${TMP_EXTRACT_DIR}/${JRE_FOLDER_NAME}" "${WIN_PKG_DIR}/jre"
rm -rf "${TMP_EXTRACT_DIR}"

# 4. Criar script de inicialização alternativa (.bat) e documentação
echo "[4/5] Criando SWStock.bat e LEIA-ME.txt..."

cat << 'BAT_EOF' > "${WIN_PKG_DIR}/SWStock.bat"
@echo off
setlocal
cd /d "%~dp0"

echo Iniciando SWStock...

if exist "jre\bin\java.exe" (
    set "JAVA_EXE=jre\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dfile.encoding=UTF-8 -jar swstock.jar

if %ERRORLEVEL% neq 0 (
    echo.
    echo Ocorreu um erro ao executar a aplicacao.
    pause
)
BAT_EOF

cat << 'TXT_EOF' > "${WIN_PKG_DIR}/LEIA-ME.txt"
============================================================
              SWStock - Versao para Windows
============================================================

COMO EXECUTAR:
1. De um duplo clique no arquivo "SWStock.exe".
2. O aplicativo abrira diretamente na sua tela!

REQUISITOS:
- Nao e necessario instalar o Java! O ambiente Java Runtime (JRE 21)
  ja esta embutido na pasta "jre" incluida neste pacote.

ARQUIVOS DO PACOTE:
- SWStock.exe   : Executavel principal da aplicacao.
- swstock.jar   : Arquivo JAR com todos os modulos e bibliotecas.
- jre/          : Runtime Java 21 para Windows de 64 bits.
- SWStock.bat   : Script de inicializacao alternativa (caso necessario depuracao).
- LEIA-ME.txt   : Este arquivo de instrucoes.

BANCO DE DADOS:
- O banco de dados SQLite ("swstock.db") sera criado e mantido
  automaticamente na mesma pasta do executavel.

DISTRIBUICAO:
- Para levar a aplicacao para outro computador Windows, basta copiar
  toda a pasta "swstock-windows" ou enviar o arquivo "SWStock-Windows-x64.zip".
============================================================
TXT_EOF

# 5. Criar arquivo ZIP final
echo "[5/5] Compactando distribuicao em SWStock-Windows-x64.zip..."
cd "${DIST_DIR}"
rm -f "SWStock-Windows-x64.zip"
zip -rq "SWStock-Windows-x64.zip" "swstock-windows"

echo "=== Empacotamento concluido com sucesso! ==="
echo "Pasta de distribuicao: ${WIN_PKG_DIR}"
echo "Arquivo ZIP gerado: ${DIST_DIR}/SWStock-Windows-x64.zip"
