package com.botvagas.model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record Vaga(
        String titulo,
        String empresa,
        String link,
        String salario,
        String localizacao,
        String tipoContrato,
        String experiencia,
        String descricao,
        List<String> beneficios,
        List<String> requisitos,
        String portal,
        LocalDateTime dataExtracao,
        boolean remoto
) {
    public Vaga(String titulo, String empresa, String link, String salario, String localizacao,
                String tipoContrato, String experiencia, String descricao, List<String> beneficios,
                List<String> requisitos, String portal, LocalDateTime dataExtracao, boolean remoto) {
        this.titulo = titulo != null ? titulo.strip() : "";
        this.empresa = empresa != null ? empresa.strip() : "";
        this.link = link != null ? link.strip() : "";
        this.salario = (salario != null && !salario.isBlank()) ? salario.strip() : "";
        this.localizacao = (localizacao != null && !localizacao.isBlank()) ? localizacao.strip() : "";
        this.tipoContrato = (tipoContrato != null && !tipoContrato.isBlank()) ? tipoContrato.strip() : "";
        this.experiencia = (experiencia != null && !experiencia.isBlank()) ? experiencia.strip() : "";
        this.descricao = descricao != null ? descricao.strip() : "";
        this.beneficios = beneficios != null ? beneficios.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(b -> !b.isBlank())
                .toList() : List.of();
        this.requisitos = requisitos != null ? requisitos.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(r -> !r.isBlank())
                .toList() : List.of();
        this.portal = (portal != null && !portal.isBlank()) ? portal.strip() : "";
        this.dataExtracao = dataExtracao != null ? dataExtracao : LocalDateTime.now();
        this.remoto = remoto;
    }

    public Vaga(String titulo, String empresa, String link) {
        this(titulo, empresa, link, "", "", "", "", "", List.of(), List.of(), "", LocalDateTime.now(), false);
    }

    public boolean isValida() {
        return !titulo.isBlank()
                && !empresa.isBlank()
                && !link.isBlank();
    }

    public String formatarParaWhatsApp() {
        StringBuilder sb = new StringBuilder();

        // TITULO E EMPRESA - DESTAQUE
        sb.append("💼 *").append(titulo).append("*\n");
        sb.append("🏢 ").append(empresa).append("\n\n");

        // SALÁRIO - Em primeiro lugar!
        if (!salario.isBlank()) {
            sb.append("💰 *Salário:* ").append(salario).append("\n");
        }

        // LOCALIZAÇÃO ou REMOTO
        if (remoto) {
            sb.append("🌐 *100% Remoto*\n");
        } else if (!localizacao.isBlank()) {
            sb.append("📍 *Local:* ").append(localizacao).append("\n");
        }

        // TIPO DE CONTRATO
        if (!tipoContrato.isBlank()) {
            sb.append("📋 *Contrato:* ").append(tipoContrato).append("\n");
        }

        // EXPERIÊNCIA
        if (!experiencia.isBlank()) {
            sb.append("🎓 *Nível:* ").append(experiencia).append("\n");
        }

        // REQUISITOS/SKILLS - O que precisa saber (até 10)
        if (!requisitos.isEmpty()) {
            sb.append("📚 *Requisitos:* ");
            // Pega até 10 requisitos
            int maxRequisitos = Math.min(requisitos.size(), 10);
            List<String> requisitosResumo = requisitos.subList(0, maxRequisitos);
            sb.append(String.join(", ", requisitosResumo));
            if (requisitos.size() > 10) {
                sb.append(" (+").append(requisitos.size() - 10).append(")");
            }
            sb.append("\n");
        }

        // BENEFÍCIOS
        if (!beneficios.isEmpty()) {
            sb.append("🎁 *Benefícios:* ");
            sb.append(String.join(", ", beneficios)).append("\n");
        }

        // DESCRIÇÃO - Mais completa agora
        if (!descricao.isBlank()) {
            sb.append("📝 *Descrição:* ").append(descricao).append("\n");
        }

        // LINK NO FINAL
        sb.append("\n🔗 ");
        String linkFormatado = link.isBlank() ? "_Link não disponível_" : link;
        sb.append(linkFormatado);

        return sb.toString();
    }

    public String formatarParaBuscaAvancada() {
        return String.format(
                "🔍 *%s* | %s | %s | %s | %s | %s%n" +
                "   📊 %s | 🕐 %s%n" +
                "   %s",
                titulo, empresa, salario, localizacao, 
                tipoContrato, (remoto ? "🌐" : "📍"),
                experiencia, dataExtracao.toLocalDate(),
                link
        );
    }
}