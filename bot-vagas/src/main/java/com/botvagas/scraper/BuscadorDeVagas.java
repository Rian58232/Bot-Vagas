package com.botvagas.scraper;

import com.botvagas.config.BotVagasProperties;
import com.botvagas.model.Vaga;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BuscadorDeVagas {
    private static final Logger log = LoggerFactory.getLogger(BuscadorDeVagas.class);

    // Seletores CSS para Indeed (multiplos seletores para compatibilidade)
    private static final String SELETOR_CARTAO = "div.job_seen_beacon, div.jobsearch-SerpJobCard, div.result, div.jobsearch-ResultsListContent div.job_seen_beacon";
    private static final String SELETOR_TITULO = "h2.jobTitle span[title], h2.jobTitle a span, span.jobTitle, h2 > a[title]";
    private static final String SELETOR_EMPRESA = "span.companyName, span[data-testid='company-name'], span.company, div.company";
    private static final String SELETOR_LINK = "h2.jobTitle a, a.jcs-JobTitle, a.jobtitle, h2 > a";
    private static final String SELETOR_SALARIO = "span[data-testid='salaryRange'], span.salary-snippet, div.salary-snippet-container";
    private static final String SELETOR_LOCALIZACAO = "div[data-testid='location'], .location, span[data-testid='location']";
    private static final String SELETOR_TIPO_CONTRATO = "div.js_job_details_underline, span.jobContext, div.job-metadata-item";
    private static final String SELETOR_DESCRICAO = "div.summary, .job-snippet, div.job-snippet, ul.job-snippet li";
    
    private static final String DOMINIO_BASE = "https://br.indeed.com";
    private static final String URL_BUSCA_INDEED = "https://br.indeed.com/jobs?q=";

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    };

    private static final int MAX_TENTATIVAS = 5;
    private static final long DELAY_MS = 3000;
    private static final long DELAY_ENTRE_REQUISICOES = 2000;
    private static final Random RANDOM = new Random();

    private final BotVagasProperties.Scraper config;
    private final ExtratorDeVagasUtil extrator;
    private long ultimaRequisicao = 0;
    private BuscadorComSelenium buscadorSelenium;

    public BuscadorDeVagas(BotVagasProperties props, ExtratorDeVagasUtil extrator) {
        this.config = props.getScraper();
        this.extrator = extrator;
    }

    @Autowired(required = false)
    public void setBuscadorSelenium(BuscadorComSelenium buscadorSelenium) {
        this.buscadorSelenium = buscadorSelenium;
    }

    public String buscarEFormatarVagas() {
        log.info(
                "Iniciando ciclo de busca de vagas | busca='{}' | url='{}'",
                descreverBusca(),
                config.getUrlBase()
        );

        List<Vaga> vagas = buscarVagas();
        if (vagas.isEmpty()) {
            return construirMensagemVazia();
        }

        return construirMensagemFinal(vagas);
    }

    public List<Vaga> buscarVagasComFiltros(FiltrosAvancados filtros) {
        log.info("Buscando vagas com filtros: {}", filtros);
        List<Vaga> todasVagas = buscarVagas();
        List<Vaga> vagasFiltradas = filtros.aplicar(todasVagas);
        log.info("Filtros aplicados: {} vagas encontradas antes, {} após filtros", 
                todasVagas.size(), vagasFiltradas.size());
        return vagasFiltradas;
    }

    private List<Vaga> buscarVagas() {
        String url = montarUrl();

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                aplicarDelayEntreRequisicoes();
                log.debug("URL de busca [tentativa {}]: {}", tentativa, url);
                Document pagina = criarConexao(url).get();
                log.info("Pagina obtida com sucesso | titulo='{}' | tentativa {}", pagina.title(), tentativa);
                return extrairVagas(pagina);
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 403) {
                    log.warn("Indeed bloqueou (403) na tentativa {}. Tentando Selenium...", tentativa);
                    // Tenta Selenium mais cedo - na 3ª tentativa ou na última
                    if (tentativa >= 3 || tentativa == MAX_TENTATIVAS) {
                        log.info("Usando fallback: Selenium com navegador real");
                        List<Vaga> vagasSelenium = buscarComSeleniumFallback();
                        if (!vagasSelenium.isEmpty()) {
                            log.info("✅ Selenium conseguiu! {} vagas extraídas", vagasSelenium.size());
                            return vagasSelenium;
                        }
                        // Se Selenium falhou, continua tentando com Jsoup se ainda houver tentativas
                        if (tentativa < MAX_TENTATIVAS) {
                            log.info("Selenium falhou, continuando com Jsoup...");
                            aguardarComBackoff(tentativa);
                            continue;
                        }
                    } else {
                        aguardarComBackoff(tentativa);
                    }
                } else if (e.getStatusCode() >= 500) {
                    log.warn("Erro do servidor {} na tentativa {}. Tentando novamente...", e.getStatusCode(), tentativa);
                    if (tentativa < MAX_TENTATIVAS) {
                        aguardarComBackoff(tentativa);
                    }
                } else {
                    log.error("Falha HTTP {} ao acessar portal: {}", e.getStatusCode(), e.getMessage());
                    return Collections.emptyList();
                }
            } catch (IOException e) {
                log.error("Falha de I/O ao acessar portal na tentativa {}: {}", tentativa, e.getMessage());
                if (tentativa < MAX_TENTATIVAS) {
                    aguardarComBackoff(tentativa);
                }
            } catch (Exception e) {
                log.error("Erro inesperado na tentativa {}: {}", tentativa, e.getMessage(), e);
                return Collections.emptyList();
            }
        }

        log.warn("Falha após {} tentativas. Tentando Selenium como último recurso...", MAX_TENTATIVAS);
        List<Vaga> vagasSelenium = buscarComSeleniumFallback();
        if (!vagasSelenium.isEmpty()) {
            log.info("✅ Selenium conseguiu como último recurso! {} vagas extraídas", vagasSelenium.size());
            return vagasSelenium;
        }

        log.error("❌ Falha total: Jsoup bloqueado (403) e Selenium também falhou.");
        return Collections.emptyList();
    }

    /**
     * Fallback para buscar vagas usando Selenium quando Jsoup é bloqueado.
     * Inclui tratamento de erro robusto.
     */
    private List<Vaga> buscarComSeleniumFallback() {
        if (buscadorSelenium == null) {
            log.warn("BuscadorComSelenium não está disponível (bean não injetado)");
            return Collections.emptyList();
        }
        
        try {
            return buscadorSelenium.buscarVagasComSelenium();
        } catch (Exception e) {
            log.error("Erro ao buscar com Selenium: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    private void aplicarDelayEntreRequisicoes() {
        long agora = System.currentTimeMillis();
        long tempoDesdeUltima = agora - ultimaRequisicao;
        if (tempoDesdeUltima < DELAY_ENTRE_REQUISICOES) {
            try {
                Thread.sleep(DELAY_ENTRE_REQUISICOES - tempoDesdeUltima);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupção durante delay entre requisições");
            }
        }
        ultimaRequisicao = System.currentTimeMillis();
    }
    
    private void aguardarComBackoff(int tentativa) {
        long tempoEspera = DELAY_MS * (long) Math.pow(2, tentativa - 1);
        log.info("Aguardando {}ms antes da próxima tentativa...", tempoEspera);
        try {
            Thread.sleep(tempoEspera);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupção durante backoff exponencial");
        }
    }

    private Connection criarConexao(String url) {
        String userAgent = USER_AGENTS[RANDOM.nextInt(USER_AGENTS.length)];
        
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "max-age=0")
                .header("Pragma", "no-cache")
                .header("Referer", "https://www.google.com/")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \";Not A Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Connection", "keep-alive")
                .header("DNT", "1")
                .timeout(config.getTimeoutMs())
                .followRedirects(true)
                .ignoreHttpErrors(false)
                .ignoreContentType(true);
    }

    private String montarUrl() {
        return config.getUrlBase() + URLEncoder.encode(montarConsulta(), StandardCharsets.UTF_8);
    }

    private List<Vaga> extrairVagas(Document pagina) {
        Elements cartoes = pagina.select(SELETOR_CARTAO);
        log.info("Cartoes de vaga encontrados no HTML: {}", cartoes.size());

        if (cartoes.isEmpty()) {
            log.warn("Nenhum cartao encontrado com o seletor '{}'.", SELETOR_CARTAO);
            return Collections.emptyList();
        }

        List<Vaga> vagas = new ArrayList<>();
        int ignoradas = 0;

        for (Element cartao : cartoes) {
            if (vagas.size() >= config.getMaximoVagas()) {
                break;
            }

            try {
                Vaga vaga = parsearCartao(cartao);
                if (vaga.isValida()) {
                    vagas.add(vaga);
                    log.debug("Vaga extraida: '{}' em '{}'", vaga.titulo(), vaga.empresa());
                } else {
                    ignoradas++;
                }
            } catch (Exception e) {
                ignoradas++;
                log.debug("Erro ao parsear cartao de vaga: {}", e.getMessage());
            }
        }

        log.info("Resultado: {} vagas validas extraidas, {} ignoradas.", vagas.size(), ignoradas);
        return vagas;
    }

    private Vaga parsearCartao(Element cartao) {
        String titulo = cartao.select(SELETOR_TITULO).attr("title");
        if (titulo.isBlank()) {
            titulo = cartao.select(SELETOR_TITULO).text();
        }
        // Fallback adicional para título
        if (titulo.isBlank()) {
            titulo = cartao.select("h2.jobTitle").text();
        }
        if (titulo.isBlank()) {
            titulo = cartao.select("a[title]").attr("title");
        }

        String empresa = cartao.select(SELETOR_EMPRESA).text();
        // Fallback adicional para empresa
        if (empresa.isBlank()) {
            empresa = cartao.select("[data-testid='company-name']").text();
        }
        
        String linkRelativo = cartao.select(SELETOR_LINK).attr("href");
        // Fallback adicional para link
        if (linkRelativo.isBlank()) {
            linkRelativo = cartao.select("a").attr("href");
        }
        
        String linkCompleto = linkRelativo.startsWith("http")
                ? linkRelativo
                : DOMINIO_BASE + linkRelativo;

        String salario = extrator.extrairSalarioReal(cartao);
        String localizacao = extrator.extrairLocalizacaoReal(cartao);
        String tipoContrato = extrator.extrairTipoContratoReal(cartao, titulo);
        String descricao = extrator.extrairDescricaoReal(cartao);
        List<String> beneficios = extrator.extrairBeneficiosReal(descricao, empresa);
        List<String> requisitos = extrator.extrairRequisitosTecnicos(descricao, titulo);
        String experiencia = extrator.extrairNivelExperienciaReal(titulo, descricao);
        boolean remoto = extrator.isRemocoReal(localizacao, descricao, titulo);

        return new Vaga(
                titulo,
                empresa,
                linkCompleto,
                salario,
                localizacao,
                tipoContrato,
                experiencia,
                descricao,
                beneficios,
                requisitos,
                "Indeed",
                LocalDateTime.now(),
                remoto
        );
    }

    private String construirMensagemVazia() {
        return String.format(
                "*Bot de Vagas*%n%n" +
                        "❌ Não consegui buscar vagas para *%s* agora.%n%n" +
                        "Motivo:%n" +
                        "- Indeed.com está bloqueando requisições automáticas%n%n" +
                        "O que fazer:%n" +
                        "- Tente novamente em alguns minutos%n" +
                        "- Acesse diretamente os portais de vagas%n%n" +
                        "Próxima tentativa agora à noite.",
                descreverBusca()
        );
    }

    private String construirMensagemFinal(List<Vaga> vagas) {
        String cabecalho = String.format(
                "*📊 Vagas do Dia - %s*%n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━%n%n",
                descreverBusca()
        );

        StringBuilder corpo = new StringBuilder();
        for (int i = 0; i < vagas.size(); i++) {
            if (i > 0) {
                corpo.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            }
            corpo.append(String.format("*[%d/%d]* ", i + 1, vagas.size())).append(vagas.get(i).formatarParaWhatsApp());
        }

        String rodape = String.format(
                "\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "*📈 Total: %d vaga(s) encontrada(s)*\n" +
                        "_Atualizado automaticamente pelo Bot de Vagas_",
                vagas.size()
        );

        return cabecalho + corpo + rodape;
    }

    private String montarConsulta() {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(config.getCargo());
        joiner.add(config.getLocalizacao());

        if (config.isIncluirRemoto()) {
            joiner.add("remoto");
        }

        if (config.isIncluirPresencial()) {
            joiner.add("presencial");
        }

        return joiner.toString();
    }

    private String descreverBusca() {
        StringBuilder descricao = new StringBuilder(config.getCargo());
        descricao.append(" | ").append(config.getLocalizacao());

        if (config.isIncluirRemoto() && config.isIncluirPresencial()) {
            descricao.append(" | remoto e presencial");
        } else if (config.isIncluirRemoto()) {
            descricao.append(" | remoto");
        } else if (config.isIncluirPresencial()) {
            descricao.append(" | presencial");
        }

        return descricao.toString();
    }
}


