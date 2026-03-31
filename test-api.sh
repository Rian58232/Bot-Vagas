#!/bin/bash
# Script de exemplo para testar os novos endpoints

echo "🚀 Testando API de Buscas Avançadas"
echo "===================================="
echo ""

BASE_URL="http://localhost:8080"

# 1. Buscar com filtros avançados
echo "1️⃣ Buscando Backend em São Paulo (Pleno/CLT)..."
curl -s "$BASE_URL/api/vagas/buscar?cargo=Backend&localizacao=São%20Paulo&experiencia=Pleno&contrato=CLT&limite=5" | jq '.vagas[0]'
echo ""

# 2. Vagas remotas
echo "2️⃣ Buscando vagas remotas de Frontend..."
curl -s "$BASE_URL/api/vagas/remotas?cargo=Frontend&limite=5" | jq '.vagas[0]'
echo ""

# 3. Filtrar por benefícios
echo "3️⃣ Buscando vagas com Home Office..."
curl -s "$BASE_URL/api/vagas/filtrar-por-beneficios?beneficio=Home%20Office&limite=5" | jq '.vagas[0]'
echo ""

# 4. Resumo
echo "4️⃣ Resumo de vagas..."
curl -s "$BASE_URL/api/vagas/resumo" | jq '.'
echo ""

# 5. Detalhe
echo "5️⃣ Detalhe de uma vaga..."
curl -s -X POST "$BASE_URL/api/vagas/detalhe" \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Backend Engineer"}' | jq '.'
echo ""

echo "✅ Testes finalizados!"
