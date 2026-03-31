package com.botvagas.scraper;

import com.botvagas.model.Vaga;
import java.util.List;
import java.util.stream.Collectors;

public class FiltrosAvancados {

    private String cargo;
    private String localizacao;
    private String salarioMinimo;
    private String salarioMaximo;
    private String nivelExperiencia;
    private String tipoContrato;
    private boolean apenasRemoto;
    private List<String> beneficiosRequiridos;

    public FiltrosAvancados() {}

    public static FiltrosAvancados novo() {
        return new FiltrosAvancados();
    }

    public FiltrosAvancados comCargo(String cargo) {
        this.cargo = cargo;
        return this;
    }

    public FiltrosAvancados comLocalizacao(String localizacao) {
        this.localizacao = localizacao;
        return this;
    }

    public FiltrosAvancados comSalarioMinimo(String salarioMinimo) {
        this.salarioMinimo = salarioMinimo;
        return this;
    }

    public FiltrosAvancados comSalarioMaximo(String salarioMaximo) {
        this.salarioMaximo = salarioMaximo;
        return this;
    }

    public FiltrosAvancados comNivelExperiencia(String nivelExperiencia) {
        this.nivelExperiencia = nivelExperiencia;
        return this;
    }

    public FiltrosAvancados comTipoContrato(String tipoContrato) {
        this.tipoContrato = tipoContrato;
        return this;
    }

    public FiltrosAvancados apenasRemoto(boolean remoto) {
        this.apenasRemoto = remoto;
        return this;
    }

    public FiltrosAvancados comBeneficios(List<String> beneficios) {
        this.beneficiosRequiridos = beneficios;
        return this;
    }

    public List<Vaga> aplicar(List<Vaga> vagas) {
        return vagas.stream()
                .filter(this::passaNoCargo)
                .filter(this::passaNaLocalizacao)
                .filter(this::passaNaExperiencia)
                .filter(this::passaNoTipoContrato)
                .filter(this::passaNoBenefico)
                .filter(this::passaNoFiltroRemoto)
                .collect(Collectors.toList());
    }

    private boolean passaNoCargo(Vaga vaga) {
        if (cargo == null || cargo.isBlank()) return true;
        return vaga.titulo().toLowerCase().contains(cargo.toLowerCase());
    }

    private boolean passaNaLocalizacao(Vaga vaga) {
        if (localizacao == null || localizacao.isBlank()) return true;
        return vaga.localizacao().toLowerCase().contains(localizacao.toLowerCase())
                || (vaga.remoto() && "remoto".equalsIgnoreCase(localizacao));
    }

    private boolean passaNaExperiencia(Vaga vaga) {
        if (nivelExperiencia == null || nivelExperiencia.isBlank()) return true;
        return vaga.experiencia().toLowerCase().contains(nivelExperiencia.toLowerCase());
    }

    private boolean passaNoTipoContrato(Vaga vaga) {
        if (tipoContrato == null || tipoContrato.isBlank()) return true;
        return vaga.tipoContrato().toLowerCase().contains(tipoContrato.toLowerCase());
    }

    private boolean passaNoBenefico(Vaga vaga) {
        if (beneficiosRequiridos == null || beneficiosRequiridos.isEmpty()) return true;
        return beneficiosRequiridos.stream()
                .anyMatch(beneficio -> vaga.beneficios().stream()
                        .anyMatch(b -> b.toLowerCase().contains(beneficio.toLowerCase())));
    }

    private boolean passaNoFiltroRemoto(Vaga vaga) {
        return !apenasRemoto || vaga.remoto();
    }

    @Override
    public String toString() {
        return String.format(
                "FiltrosAvancados{cargo='%s', localizacao='%s', salario='%s-%s', " +
                        "experiencia='%s', contrato='%s', remoto=%s}",
                cargo, localizacao, salarioMinimo, salarioMaximo, 
                nivelExperiencia, tipoContrato, apenasRemoto
        );
    }
}
