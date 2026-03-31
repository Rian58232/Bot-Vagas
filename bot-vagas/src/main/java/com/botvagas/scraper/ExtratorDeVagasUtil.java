package com.botvagas.scraper;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExtratorDeVagasUtil {

    public String extrairSalarioReal(Element cartao) {
        // Busca salário de forma SIMPLES e DIRETA
        String textoCompleto = cartao.text();
        
        // Padrões MAIS simples e diretos
        String[] padroes = {
            "r\\$\\s*[\\d.,]+",           // R$ 5000 ou R$ 5.000
            "[\\d.,]+\\s*reais",          // 5000 reais
            "r\\$\\s*[\\d.,]+\\s*/\\s*m", // R$ 5000/m
            "us\\$\\s*[\\d.,]+",          // US$ 5000
            "\\$\\s*[\\d.,]+",            // $ 5000
            "€\\s*[\\d.,]+",              // € 5000
            "£\\s*[\\d.,]+"               // £ 5000
        };
        
        for (String padrao : padroes) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(padrao, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(textoCompleto);
            if (matcher.find()) {
                return matcher.group().trim().toUpperCase();
            }
        }
        
        return "";
    }

    private String buscarSalarioNosSeletores(Element cartao) {
        // Lista de seletores que o Indeed usa pra salário
        String[] seletores = {
            "span[data-testid='salaryRange']",
            ".salary-snippet",
            ".salary-snippet-container",
            "[data-salary-currency]",
            ".compensation",
            ".salary-info",
            ".salary-text",
            ".job-compensation",
            "span[class*='salary']",
            "div[class*='salary']",
            "span[class*='compensation']",
            "div[class*='compensation']"
        };
        
        for (String seletor : seletores) {
            Elements elementos = cartao.select(seletor);
            for (Element el : elementos) {
                String texto = el.text().trim();
                if (!texto.isBlank() && temTextoDeSalario(texto)) {
                    return formatarSalario(texto);
                }
                // Tenta pegar do atributo title também
                String title = el.attr("title");
                if (!title.isBlank() && temTextoDeSalario(title)) {
                    return formatarSalario(title);
                }
            }
        }
        
        return "";
    }

    private String buscarSalarioEmElementsEspecificos(Element cartao) {
        // Busca em spans e divs que contenham padrões de salário
        Elements todosElementos = cartao.select("*");
        
        for (Element el : todosElementos) {
            String texto = el.text().trim();
            
            // Ignora textos muito curtos ou muito longos
            if (texto.length() < 5 || texto.length() > 100) {
                continue;
            }
            
            if (temTextoDeSalario(texto)) {
                return formatarSalario(texto);
            }
        }
        
        return "";
    }

    private boolean temTextoDeSalario(String texto) {
        String textoLower = texto.toLowerCase();
        
        // Palavras-chave que indicam salário
        String[] indicadores = {
            "r$", "reais", "por mês", "ao mês", "mensal", "mensais",
            "por ano", "ao ano", "anual", "anuais",
            "us$", "dólares", "dollars", "usd",
            "€", "euros", "eur",
            "£", "libras", "pounds", "gbp",
            "salary", "pay", "wage", "compensation",
            "faixa salarial", "range salarial"
        };
        
        for (String indicador : indicadores) {
            if (textoLower.contains(indicador)) {
                return true;
            }
        }
        
        // Também verifica se tem padrão numérico de valor (ex: 5.000, 10000)
        if (texto.matches(".*[R$€£]?\\s*\\d{1,3}[.,]\\d{3}.*")) {
            return true;
        }
        
        return false;
    }

    private String formatarSalario(String texto) {
        // Limpa e formata o salário
        return texto.trim().replaceAll("\\s+", " ");
    }

    /**
     * Extrai salário de texto livre usando regex.
     */
    private String extrairSalarioDoTexto(String texto) {
        // Regex patterns pra salário - do mais específico pro mais genérico
        String[] padroes = {
            // Real brasileiro - R$
            "r\\$\\s*[\\d.,]+\\s*(?:por|ao)?\\s*(?:mês|mes|mensal|ano)?",  // R$ 5000, R$ 5.000 por mês
            "r\\$\\s*[\\d.,]+\\s*/\\s*(?:mês|mes|ano)",  // R$ 5000/mês
            "[\\d.,]+\\s*reais\\s*(?:por|ao)?\\s*(?:mês|mes|mensal|ano)?", // 5000 reais por mês
            
            // Por hora
            "[\\d.,]+\\s*(?:por|ao|a)\\s*(?:hora|hr|h)",  // 50 por hora
            
            // Genérico com mês/ano
            "[\\d.,]+\\s*(?:por|ao)\\s*(?:mês|mes|mensal|ano)",  // 5000 por mês
            "[\\d.,]+\\s*mensais",  // 5000 mensais
            "[\\d.,]+\\s*(?:por|ao)\\s*ano",  // 5000 por ano
            
            // Dólar
            "us\\$\\s*[\\d.,]+\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // US$ 5000
            "[\\d.,]+\\s*dólares\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // 5000 dólares
            "\\$\\s*[\\d.,]+\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // $ 5000
            
            // Euro
            "€\\s*[\\d.,]+\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // € 5000
            "[\\d.,]+\\s*euros\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // 5000 euros
            "eur\\s*[\\d.,]+",  // EUR 5000
            
            // Libra
            "£\\s*[\\d.,]+\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // £ 5000
            "[\\d.,]+\\s*libras\\s*(?:por|ao)?\\s*(?:mês|mes|ano|hora)?",  // 5000 libras
            "gbp\\s*[\\d.,]+",  // GBP 5000
            
            // Faixa salarial
            "[\\d.,]+\\s*(?:a|-)\\s*[\\d.,]+\\s*(?:reais|r\\$|dólares|us\\$|euros|€)?\\s*(?:por|ao)?\\s*(?:mês|mes|ano)?",  // 5000 a 7000
            "faixa.*?[\\d.,]+\\s*(?:a|-)\\s*[\\d.,]+",  // faixa de 5000 a 7000
            "range.*?[\\d.,]+\\s*(?:a|-|to)\\s*[\\d.,]+",  // range 5000 to 7000
            "between.*?[\\d.,]+\\s*(?:and|to)\\s*[\\d.,]+",  // between 5000 and 7000
            
            // Apenas números com contexto
            "(?:salário|salario|pay|salary|wage).*?[\\d.,]+",  // salário 5000
            "(?:remuneração|remuneracao).*?[\\d.,]+",  // remuneração 5000
            "(?:vencimento|vencimentos).*?[\\d.,]+",  // vencimentos 5000
        };
        
        for (String padrao : padroes) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(padrao, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(texto);
            if (matcher.find()) {
                String resultado = matcher.group();
                // Formata: remove espaços extras e deixa legível
                return resultado.trim().replaceAll("\\s+", " ");
            }
        }
        
        return "";
    }

    /**
     * Busca salário na página individual da vaga (mais detalhado).
     * Este método deve ser chamado com o HTML completo da página da vaga.
     */
    public String extrairSalarioDaPaginaVaga(Element paginaCompleta) {
        // Estratégia 1: Seletores específicos da página da vaga
        String[] seletoresPagina = {
            "#salaryInfoAndJobType",
            ".jobsearch-JobComponent-salary",
            ".salaryAndLocationContainer",
            "[data-testid='salaryText']",
            ".compensationAndBenefits",
            "#jobDescription div[class*='salary']",
            "span[id*='salary']",
            "div[id*='salary']"
        };
        
        for (String seletor : seletoresPagina) {
            Elements elementos = paginaCompleta.select(seletor);
            for (Element el : elementos) {
                String texto = el.text().trim();
                if (!texto.isBlank() && temTextoDeSalario(texto)) {
                    return formatarSalario(texto);
                }
            }
        }
        
        // Estratégia 2: Buscar em TODO o texto da página
        String textoCompleto = paginaCompleta.text();
        return extrairSalarioDoTexto(textoCompleto);
    }

    public String extrairLocalizacaoReal(Element cartao) {
        String localizacao = cartao.select(".location span").text();
        if (localizacao.isBlank()) {
            localizacao = cartao.select("div[data-testid='location']").text();
        }
        if (localizacao.isBlank()) {
            localizacao = cartao.select("[data-tn-component-context*='location']").text();
        }
        if (localizacao.isBlank()) {
            localizacao = cartao.select(".companyLocation").text();
        }
        
        if (!localizacao.isBlank()) {
            return localizacao.strip();
        }
        
        return "Brasil";
    }

    public String extrairTipoContratoReal(Element cartao, String titulo) {
        String texto = cartao.text().toLowerCase();
        
        if (texto.contains("clt") || titulo.toLowerCase().contains("clt")) {
            return "CLT";
        }
        if (texto.contains("pj") || texto.contains("pessoa jurídica") || titulo.toLowerCase().contains("pj")) {
            return "PJ";
        }
        if (texto.contains("autônomo") || texto.contains("freelancer")) {
            return "Autônomo/Freelancer";
        }
        if (texto.contains("temporário") || texto.contains("contrato por prazo")) {
            return "Temporário";
        }
        if (texto.contains("estágio") || titulo.toLowerCase().contains("estágio")) {
            return "Estágio";
        }
        
        return "CLT";
    }

    public String extrairDescricaoReal(Element cartao) {
        // Pega os bullet points da descrição
        String descricao = cartao.select(".job-snippet li").stream()
                .map(Element::text)
                .filter(t -> !t.isBlank())
                .limit(3)  // Pega só 3 itens
                .reduce((a, b) -> a + " • " + b)
                .orElse("");
        
        if (descricao.isBlank()) {
            descricao = cartao.select(".summary").text();
        }
        
        if (descricao.isBlank()) {
            descricao = cartao.text();
        }

        // Limite MENOR - 250 caracteres
        int limite = 250;
        if (descricao.length() > limite) {
            String substr = descricao.substring(0, limite);
            int ultimoPonto = substr.lastIndexOf('.');
            
            if (ultimoPonto > 80) {
                descricao = substr.substring(0, ultimoPonto + 1) + "...";
            } else {
                int ultimoEspaco = substr.lastIndexOf(' ');
                descricao = ultimoEspaco > 0 
                    ? substr.substring(0, ultimoEspaco) + "..." 
                    : substr + "...";
            }
        }

        return descricao.strip();
    }

    private String limparTextoDescricao(String texto) {
        // Remove informações que já estão em outros campos (título, empresa)
        return texto;  // Retorna o texto completo por enquanto
    }

    /**
     * Extrai informações sobre tipo de contrato e salário da descrição.
     */
    public String extrairInformacoesContrato(String descricao, String titulo) {
        List<String> infos = new ArrayList<>();
        String texto = (descricao + " " + titulo).toLowerCase();
        
        // Tipo de contrato
        if (texto.contains("pj") || texto.contains("pessoa jurídica")) {
            infos.add("PJ");
        } else if (texto.contains("clt")) {
            infos.add("CLT");
        } else if (texto.contains("estágio")) {
            infos.add("Estágio");
        }
        
        return infos.isEmpty() ? "" : String.join(" | ", infos);
    }

    public List<String> extrairBeneficiosReal(String descricao, String empresa) {
        List<String> beneficios = new ArrayList<>();
        String textoCompleto = (descricao + " " + empresa).toLowerCase();

        // Benefícios com possíveis valores
        Map<String, String[]> beneficiosMap = new LinkedHashMap<>();
        beneficiosMap.put("Vale Refeição", new String[]{"vale refeição", "vale ref", "vr ", "alimentação", "caju"});
        beneficiosMap.put("Vale Transporte", new String[]{"vale transporte", "vale trans", "vt ", "transporte"});
        beneficiosMap.put("Plano de Saúde", new String[]{"plano de saúde", "convênio médico", "conv médico", "saúde", "assistência médica"});
        beneficiosMap.put("Plano Odontológico", new String[]{"plano odontológico", "convênio odontológico", "odontológico", "dental", "assistência odontológica"});
        beneficiosMap.put("Home Office", new String[]{"home office", "home-office", "remoto", "flexível", "trabalho remoto", "100% remoto"});
        beneficiosMap.put("Auxílio Creche", new String[]{"auxílio creche", "creche", "maternal", "baby gift"});
        beneficiosMap.put("13º Salário", new String[]{"13º salário", "décimo terceiro", "décimo 3º", "gratificação natalina"});
        beneficiosMap.put("FGTS", new String[]{"fgts", "fundo de garantia"});
        beneficiosMap.put("Bônus", new String[]{"bônus", "bonus", "participação nos lucros", "ppl", "gratificação", "bônus anual"});
        beneficiosMap.put("Seguro de Vida", new String[]{"seguro de vida", "seguro vida"});
        beneficiosMap.put("Auxílio Cultura", new String[]{"auxílio cultura", "cultura", "livros", "cursos"});
        beneficiosMap.put("Zenklub", new String[]{"zenklub", "terapia", "psicólogo"});
        beneficiosMap.put("Gympass/Wellhub", new String[]{"gympass", "wellhub", "academia", "atividade física"});
        beneficiosMap.put("Recesso Remunerado", new String[]{"recesso remunerado", "30 dias recesso", "férias remuneradas"});
        beneficiosMap.put("Day Off", new String[]{"day off", "dia aniversário", "aniversário"});
        beneficiosMap.put("No Dress Code", new String[]{"no dress code", "sem código de vestimenta", "roupa livre"});
        beneficiosMap.put("Equipamentos Incluídos", new String[]{"equipamentos", "notebook fornecido", "estrutura completa"});

        for (Map.Entry<String, String[]> entry : beneficiosMap.entrySet()) {
            for (String palavra : entry.getValue()) {
                if (textoCompleto.contains(palavra)) {
                    beneficios.add(entry.getKey());
                    break;
                }
            }
        }

        return beneficios;
    }

    public String extrairNivelExperienciaReal(String titulo, String descricao) {
        String textoCompleto = (titulo + " " + descricao).toLowerCase();

        if (textoCompleto.contains("senior") || textoCompleto.contains("sênior") || 
            textoCompleto.contains("lead") || textoCompleto.contains("principal") ||
            textoCompleto.contains("diretor") || textoCompleto.contains("manager")) {
            return "Senior (5+ anos)";
        }
        
        if (textoCompleto.contains("pleno") || textoCompleto.contains("mid-level") ||
            textoCompleto.contains("3 a 5 anos") || textoCompleto.contains("3-5 anos")) {
            return "Pleno (3-5 anos)";
        }
        
        if (textoCompleto.contains("junior") || textoCompleto.contains("júnior") ||
            textoCompleto.contains("0 a 3 anos") || textoCompleto.contains("0-3 anos") ||
            textoCompleto.contains("iniciante") || textoCompleto.contains("entry-level")) {
            return "Junior (0-3 anos)";
        }
        
        if (textoCompleto.contains("estágio") || textoCompleto.contains("trainee") ||
            textoCompleto.contains("aprendiz")) {
            return "Trainee/Estágio";
        }

        if (textoCompleto.contains("ano") && textoCompleto.contains("experiência")) {
            return "Pleno (3-5 anos)";
        }

        return "Não especificado";
    }

    public boolean isRemocoReal(String localizacao, String descricao, String titulo) {
        String textoCompleto = (localizacao + " " + descricao + " " + titulo).toLowerCase();

        return textoCompleto.contains("remoto")
                || textoCompleto.contains("home office")
                || textoCompleto.contains("100% remoto")
                || textoCompleto.contains("totalmente remoto")
                || textoCompleto.contains("trabalho remoto")
                || textoCompleto.contains("anywhere")
                || textoCompleto.contains("remote");
    }

    /**
     * Extrai requisitos técnicos da vaga (skills, linguagens, frameworks).
     * Prioriza termos específicos e evita falsos positivos.
     */
    public List<String> extrairRequisitosTecnicos(String descricao, String titulo) {
        List<String> requisitos = new ArrayList<>();
        String textoCompleto = (descricao + " " + titulo).toLowerCase();

        // Linguagens de Programação (com contexto pra evitar falsos positivos)
        String[] linguagens = {
            "java", "python", "javascript", "typescript", "c#", "c++", "golang", "go language",
            "ruby", "php", "swift", "kotlin", "scala", "rust", "r language", "matlab"
        };

        // Frameworks e Bibliotecas
        String[] frameworks = {
            "spring boot", "spring framework", "spring mvc", "spring data", "spring security",
            "hibernate", "jsf", "primefaces",
            "react", "react.js", "reactjs", "next.js", "nextjs",
            "angular", "vue.js", "vuejs", "vue",
            "node.js", "nodejs", "express.js", "express", "nest.js", "nestjs",
            "django", "flask", "fastapi", "laravel", "symfony", "codeigniter",
            ".net", "asp.net", "asp.net core", "entity framework", "ef core",
            "tensorflow", "pytorch", "scikit-learn", "pandas", "numpy", "keras"
        };

        // Banco de Dados
        String[] bancosDados = {
            "sql", "mysql", "postgresql", "postgres", "oracle database", "oracle",
            "sql server", "mssql", "mongodb", "mongo", "redis", "elasticsearch",
            "cassandra", "dynamodb", "firebase", "supabase", "mariadb", "sqlite"
        };

        // DevOps e Cloud
        String[] devops = {
            "docker", "kubernetes", "k8s", "aws", "amazon web services",
            "azure", "gcp", "google cloud platform", "google cloud",
            "terraform", "ansible", "jenkins", "gitlab ci", "github actions",
            "circleci", "travis ci", "ci/cd", "devops", "linux", "bash", "shell script",
            "powershell", "prometheus", "grafana", "datadog"
        };

        // Ferramentas e Outros
        String[] ferramentas = {
            "git", "github", "gitlab", "bitbucket", "jira", "confluence", "trello",
            "scrum", "kanban", "agile", "metodologias ágeis", "xp", "pair programming",
            "api", "rest", "restful", "rest api", "graphql", "grpc", "soap",
            "microserviços", "microservices", "arquitetura de microserviços",
            "clean code", "clean architecture", "ddd", "domain driven design",
            "tdd", "test driven development", "bdd", "solid", "design patterns"
        };

        // Front-end e Mobile
        String[] frontend = {
            "html5", "html", "css3", "css", "sass", "scss", "less",
            "bootstrap", "tailwind", "material-ui", "mui", "chakra ui",
            "redux", "mobx", "context api", "webpack", "vite", "babel",
            "jest", "cypress", "playwright", "testing library",
            "ios", "android", "react native", "flutter", "dart", "xamarin", "ionic"
        };

        // Verifica cada categoria (na ordem de prioridade)
        verificarExtrair(requisitos, textoCompleto, frontend, true);  // Prioriza front-end
        verificarExtrair(requisitos, textoCompleto, frameworks, true);
        verificarExtrair(requisitos, textoCompleto, linguagens, true);
        verificarExtrair(requisitos, textoCompleto, bancosDados, false);
        verificarExtrair(requisitos, textoCompleto, devops, false);
        verificarExtrair(requisitos, textoCompleto, ferramentas, false);

        return requisitos;
    }

    private void verificarExtrair(List<String> requisitos, String texto, String[] termos, boolean prioridade) {
        for (String termo : termos) {
            // Verifica se o termo existe no texto
            if (texto.contains(termo.toLowerCase())) {
                // Evita falsos positivos de letras únicas
                if (termo.length() <= 2 && !termo.matches("(?i)c\\+\\+|c#|go|r language")) {
                    // Termos curtos só entram se tiver contexto
                    if (!temContextoTecnico(texto, termo)) {
                        continue;
                    }
                }
                
                // Adiciona o termo com formatação original
                if (!requisitos.contains(termo)) {
                    if (prioridade && requisitos.size() >= 8) {
                        // Se já tem muitos requisitos prioritários, pula
                        continue;
                    }
                    requisitos.add(termo);
                }
            }
        }
    }

    private boolean temContextoTecnico(String texto, String termo) {
        // Verifica se o termo curto aparece em contexto técnico
        String contexto = termo.toLowerCase();
        
        if (contexto.equals("c")) {
            return texto.contains("linguagem c") || texto.contains("programação c") || 
                   texto.contains("c/c++") || texto.contains("ansi c");
        }
        if (contexto.equals("r")) {
            return texto.contains("linguagem r") || texto.contains("r programming") || 
                   texto.contains("r language") || texto.contains("programação r");
        }
        if (contexto.equals("go")) {
            return texto.contains("golang") || texto.contains("go language") || 
                   texto.contains("linguagem go");
        }
        
        return true;  // Para outros termos
    }
}
