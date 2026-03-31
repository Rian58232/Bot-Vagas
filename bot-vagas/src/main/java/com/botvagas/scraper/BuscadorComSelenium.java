package com.botvagas.scraper;

import com.botvagas.config.BotVagasProperties;
import com.botvagas.model.Vaga;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BuscadorComSelenium {
    private static final Logger log = LoggerFactory.getLogger(BuscadorComSelenium.class);

    private static final String SELETOR_CARTAO = "div.job_seen_beacon, div.jobsearch-SerpJobCard, div.result";
    private static final String SELETOR_TITULO = "h2.jobTitle span[title], h2.jobTitle a span, span.jobTitle";
    private static final String SELETOR_EMPRESA = "span.companyName, span[data-testid='company-name'], span.company";
    private static final String SELETOR_LINK = "h2.jobTitle a, a.jcs-JobTitle, a.jobtitle";
    
    private static final String DOMINIO_BASE = "https://br.indeed.com";
    private static final String URL_BUSCA_INDEED = "https://br.indeed.com/jobs?q=";

    private final BotVagasProperties.Scraper config;
    private final ExtratorDeVagasUtil extrator;

    public BuscadorComSelenium(BotVagasProperties props, ExtratorDeVagasUtil extrator) {
        this.config = props.getScraper();
        this.extrator = extrator;
    }

    public List<Vaga> buscarVagasComSelenium() {
        WebDriver driver = null;
        try {
            driver = criarDriver();
            String url = montarUrl();
            log.info("Acessando Indeed com Selenium: {}", url);

            driver.get(url);
            
            // Esperar mais tempo para a página carregar completamente
            Thread.sleep(5000);
            
            // Tentar scroll para carregar mais conteúdo
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(2000);
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
                Thread.sleep(1000);
            } catch (Exception e) {
                log.warn("Não foi possível fazer scroll: {}", e.getMessage());
            }

            String html = driver.getPageSource();
            Document pagina = Jsoup.parse(html);

            log.info("Página carregada via Selenium. Título: {}", pagina.title());
            
            // Salvar HTML pra debug (opcional - descomente se precisar diagnosticar)
            // try {
            //     java.nio.file.Files.writeString(
            //         java.nio.file.Paths.get("indeed-debug-" + System.currentTimeMillis() + ".html"),
            //         html
            //     );
            //     log.info("HTML salvo em: indeed-debug-{}.html", System.currentTimeMillis());
            // } catch (Exception e) {
            //     log.warn("Não foi possível salvar HTML de debug: {}", e.getMessage());
            // }
            
            // Verificar se a página tem conteúdo de bloqueio do Indeed
            String textoPagina = pagina.body() != null ? pagina.body().text().toLowerCase() : "";
            if (textoPagina.contains("acesso negado") || 
                textoPagina.contains("blocked") ||
                textoPagina.contains("403") ||
                textoPagina.contains("access denied")) {
                log.warn("Selenium detectou página de bloqueio do Indeed");
                return Collections.emptyList();
            }
            
            return extrairVagasDoHtml(pagina);

        } catch (Exception e) {
            log.error("Erro ao buscar com Selenium: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Erro ao fechar driver: {}", e.getMessage());
                }
            }
        }
    }

    private WebDriver criarDriver() {
        ChromeOptions options = new ChromeOptions();

        // Configurações para parecer um navegador real e evitar detecção
        options.addArguments(
                "--start-maximized",
                "--disable-blink-features=AutomationControlled",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--disable-web-resources",
                "--disable-plugins",
                "--disable-images",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--disable-notifications",
                "--disable-popup-blocking",
                "--ignore-certificate-errors",
                "--test-type",
                "--lang=pt-BR"
        );

        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation", "load-extension"));
        options.setExperimentalOption("useAutomationExtension", false);
        
        // Adicionar argumentos de user-agent e platform
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        options.addArguments("--lang=pt-BR");

        String chromeDriverPath = System.getProperty("user.home") + "/Downloads/bot-vagas/chromedriver.exe";
        File chromeDriver = new File(chromeDriverPath);

        if (!chromeDriver.exists()) {
            chromeDriverPath = "./chromedriver.exe";
            chromeDriver = new File(chromeDriverPath);
        }

        if (!chromeDriver.exists()) {
            chromeDriverPath = "C:/Users/gusta/Downloads/bot-vagas/chromedriver.exe";
            chromeDriver = new File(chromeDriverPath);
        }

        log.info("Usando chromedriver em: {}", chromeDriver.getAbsolutePath());

        if (!chromeDriver.exists()) {
            log.error("❌ ChromeDriver não encontrado! Verifique se o arquivo existe.");
            throw new RuntimeException("ChromeDriver não encontrado em: " + chromeDriver.getAbsolutePath());
        }

        System.setProperty("webdriver.chrome.driver", chromeDriver.getAbsolutePath());
        System.setProperty("webdriver.chrome.whitelistedIps", "");
        
        return new ChromeDriver(options);
    }

    private String montarUrl() {
        String consulta = config.getCargo() + " " + config.getLocalizacao();
        return URL_BUSCA_INDEED + URLEncoder.encode(consulta, StandardCharsets.UTF_8);
    }

    private List<Vaga> extrairVagasDoHtml(Document pagina) {
        Elements cartoes = pagina.select(SELETOR_CARTAO);
        log.info("Cartões encontrados: {}", cartoes.size());

        if (cartoes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Vaga> vagas = new ArrayList<>();
        Set<String> linksVistos = new HashSet<>();  // Evita repetidas
        int ignoradas = 0;
        int totalProcessados = 0;
        int repetidas = 0;

        for (Element cartao : cartoes) {
            totalProcessados++;
            if (vagas.size() >= config.getMaximoVagas()) {
                break;
            }

            try {
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

                // VERIFICA SE JÁ VIU ESSA VAGA
                if (linksVistos.contains(linkCompleto)) {
                    log.debug("Vaga repetida ignorada: {}", titulo);
                    repetidas++;
                    continue;
                }
                linksVistos.add(linkCompleto);

                // Extrai salário antes do log
                String salario = extrator.extrairSalarioReal(cartao);
                
                // Log EXTRA detalhado do salário
                if (!salario.isBlank()) {
                    log.info("💰 SALÁRIO ENCONTRADO no cartão {}: '{}'", totalProcessados, salario);
                } else {
                    log.warn("❌ Salário NÃO encontrado no cartão {}", totalProcessados);
                }

                // Log detalhado pra debug
                log.debug("Cartão {}: titulo='{}', empresa='{}', link='{}', salario='{}'", 
                    totalProcessados, 
                    titulo.length() > 50 ? titulo.substring(0, 50) + "..." : titulo,
                    empresa.length() > 50 ? empresa.substring(0, 50) + "..." : empresa,
                    linkCompleto.length() > 80 ? linkCompleto.substring(0, 80) + "..." : linkCompleto,
                    salario.isBlank() ? "N/A" : salario);

                if (titulo.isBlank() || empresa.isBlank() || linkCompleto.isBlank()) {
                    log.debug("Cartão {} ignorado: titulo={}, empresa={}, link={}", 
                        totalProcessados, titulo.isBlank(), empresa.isBlank(), linkCompleto.isBlank());
                    ignoradas++;
                    continue;
                }

                // Reutilizar métodos de extração da utility
                String localizacao = extrator.extrairLocalizacaoReal(cartao);
                String tipoContrato = extrator.extrairTipoContratoReal(cartao, titulo);
                String descricao = extrator.extrairDescricaoReal(cartao);
                List<String> beneficios = extrator.extrairBeneficiosReal(descricao, empresa);
                List<String> requisitos = extrator.extrairRequisitosTecnicos(descricao, titulo);
                String experiencia = extrator.extrairNivelExperienciaReal(titulo, descricao);
                boolean remoto = extrator.isRemocoReal(localizacao, descricao, titulo);

                Vaga vaga = new Vaga(
                        titulo, empresa, linkCompleto, salario, localizacao,
                        tipoContrato, experiencia, descricao, beneficios,
                        requisitos, "Indeed", LocalDateTime.now(), remoto
                );

                if (vaga.isValida()) {
                    vagas.add(vaga);
                    log.info("✅ Vaga extraída (Selenium): {} - {}", vaga.titulo(), vaga.empresa());
                } else {
                    ignoradas++;
                    log.debug("Vaga inválida após criação: {}", vaga);
                }
            } catch (Exception e) {
                ignoradas++;
                log.debug("Erro ao parsear cartão {}: {}", totalProcessados, e.getMessage());
            }
        }

        log.info("Resultado Selenium: {} vagas válidas, {} repetidas, {} ignoradas de {} processados", 
            vagas.size(), repetidas, ignoradas, Math.min(totalProcessados, cartoes.size()));
        return vagas;
    }
}
