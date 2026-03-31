package com.botvagas.api;

import com.botvagas.model.Vaga;
import com.botvagas.scraper.BuscadorDeVagas;
import com.botvagas.scraper.FiltrosAvancados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vagas")
public class BuscasAvancadasController {
    
    private static final Logger log = LoggerFactory.getLogger(BuscasAvancadasController.class);
    private final BuscadorDeVagas buscador;

    public BuscasAvancadasController(BuscadorDeVagas buscador) {
        this.buscador = buscador;
    }

    @GetMapping("/buscar")
    public ResponseEntity<BuscaResponse> buscarComFiltros(
            @RequestParam(required = false) String cargo,
            @RequestParam(required = false) String localizacao,
            @RequestParam(required = false) String experiencia,
            @RequestParam(required = false) String contrato,
            @RequestParam(required = false, defaultValue = "false") boolean apenasRemoto,
            @RequestParam(required = false, defaultValue = "50") int limite
    ) {
        log.info("Busca avançada: cargo={}, localizacao={}, experiencia={}, contrato={}, remoto={}", 
                cargo, localizacao, experiencia, contrato, apenasRemoto);

        FiltrosAvancados filtros = FiltrosAvancados.novo()
                .comCargo(cargo)
                .comLocalizacao(localizacao)
                .comNivelExperiencia(experiencia)
                .comTipoContrato(contrato)
                .apenasRemoto(apenasRemoto);

        List<Vaga> vagas = buscador.buscarVagasComFiltros(filtros);
        List<Vaga> vagasLimitadas = vagas.stream()
                .limit(limite)
                .collect(Collectors.toList());

        BuscaResponse resposta = new BuscaResponse(
                vagasLimitadas.size(),
                vagasLimitadas,
                cargo,
                localizacao,
                experiencia,
                contrato,
                apenasRemoto
        );

        log.info("Busca avançada retornou {} vagas", vagasLimitadas.size());
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/filtrar-por-beneficios")
    public ResponseEntity<BuscaResponse> buscarComBeneficios(
            @RequestParam String beneficio,
            @RequestParam(required = false) String cargo,
            @RequestParam(required = false, defaultValue = "50") int limite
    ) {
        log.info("Busca por benefícios: beneficio={}, cargo={}", beneficio, cargo);

        FiltrosAvancados filtros = FiltrosAvancados.novo()
                .comCargo(cargo)
                .comBeneficios(List.of(beneficio));

        List<Vaga> vagas = buscador.buscarVagasComFiltros(filtros);
        List<Vaga> vagasLimitadas = vagas.stream()
                .limit(limite)
                .collect(Collectors.toList());

        BuscaResponse resposta = new BuscaResponse(
                vagasLimitadas.size(),
                vagasLimitadas,
                cargo,
                null,
                null,
                null,
                false
        );

        log.info("Busca por benefício retornou {} vagas", vagasLimitadas.size());
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/remotas")
    public ResponseEntity<BuscaResponse> buscarVagasRemotas(
            @RequestParam(required = false) String cargo,
            @RequestParam(required = false, defaultValue = "50") int limite
    ) {
        log.info("Busca vagas remotas: cargo={}", cargo);

        FiltrosAvancados filtros = FiltrosAvancados.novo()
                .comCargo(cargo)
                .apenasRemoto(true);

        List<Vaga> vagas = buscador.buscarVagasComFiltros(filtros);
        List<Vaga> vagasLimitadas = vagas.stream()
                .limit(limite)
                .collect(Collectors.toList());

        BuscaResponse resposta = new BuscaResponse(
                vagasLimitadas.size(),
                vagasLimitadas,
                cargo,
                "Remoto",
                null,
                null,
                true
        );

        log.info("Busca vagas remotas retornou {} vagas", vagasLimitadas.size());
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/resumo")
    public ResponseEntity<ResumoVagasResponse> obterResumoVagas() {
        log.info("Gerando resumo de vagas");

        FiltrosAvancados filtros = FiltrosAvancados.novo();
        List<Vaga> todasVagas = buscador.buscarVagasComFiltros(filtros);

        long totalVagas = todasVagas.size();
        long vagasRemotas = todasVagas.stream().filter(Vaga::remoto).count();
        long vagasPresenciais = totalVagas - vagasRemotas;
        
        List<String> portais = todasVagas.stream()
                .map(Vaga::portal)
                .distinct()
                .collect(Collectors.toList());

        List<String> empresas = todasVagas.stream()
                .map(Vaga::empresa)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        List<String> nivelExperiencia = todasVagas.stream()
                .map(Vaga::experiencia)
                .distinct()
                .collect(Collectors.toList());

        ResumoVagasResponse resumo = new ResumoVagasResponse(
                totalVagas,
                vagasRemotas,
                vagasPresenciais,
                portais,
                empresas,
                nivelExperiencia
        );

        return ResponseEntity.ok(resumo);
    }

    @PostMapping("/detalhe")
    public ResponseEntity<Vaga> obterDetalheVaga(@RequestBody BuscaDetalheRequest request) {
        log.info("Buscando detalhe da vaga: {}", request.titulo());

        FiltrosAvancados filtros = FiltrosAvancados.novo()
                .comCargo(request.titulo());

        List<Vaga> vagas = buscador.buscarVagasComFiltros(filtros);
        
        if (vagas.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(vagas.get(0));
    }

    public record BuscaResponse(
            int total,
            List<Vaga> vagas,
            String cargo,
            String localizacao,
            String experiencia,
            String contrato,
            boolean remoto
    ) {}

    public record ResumoVagasResponse(
            long totalVagas,
            long vagasRemotas,
            long vagasPresenciais,
            List<String> portais,
            List<String> empresas,
            List<String> nivelExperiencia
    ) {}

    public record BuscaDetalheRequest(String titulo) {}
}
