package io.github.patriciastarck.literalura.principal;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.patriciastarck.literalura.dto.LivroDTO;
import io.github.patriciastarck.literalura.model.Autor;
import io.github.patriciastarck.literalura.model.Livro;
import io.github.patriciastarck.literalura.repository.LivroRepository;
import io.github.patriciastarck.literalura.service.ConsumoAPI;
import io.github.patriciastarck.literalura.service.ConverteDados;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MenuPrincipal {

    @Autowired
    private LivroRepository livroRepository;

    @Autowired
    private ConsumoAPI consumoAPI;

    @Autowired
    private ConverteDados converteDados;

    private final Scanner leitura = new Scanner(System.in);

    public MenuPrincipal(LivroRepository livroRepository, ConsumoAPI consumoAPI, ConverteDados converteDados) {
        this.livroRepository = livroRepository;
        this.consumoAPI = consumoAPI;
        this.converteDados = converteDados;
    }

    public void executar() {
        boolean running = true;
        while (running) {
            exibirMenu();
            var opcao = leitura.nextInt();
            leitura.nextLine();

            switch (opcao) {
                case 1 -> buscarLivrosPeloTitulo();
                case 2 -> listarLivrosRegistrados();
                case 3 -> listarAutoresRegistrados();
                case 4 -> listarAutoresVivos();
                case 5 -> listarAutoresVivosRefinado();
                case 6 -> listarAutoresPorAnoDeMorte();
                case 7 -> listarLivrosPorIdioma();
                case 0 -> {
                    System.out.println("Encerrando a LiterAlura!");
                    running = false;
                }
                default -> System.out.println("Opção inválida!");
            }
        }
    }

    private void exibirMenu() {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                       LITERALURA                               ║");
        System.out.println("║           Uma aplicação para você que ama livros!              ║");
        System.out.println("║                                                                ║");
        System.out.println("║                  Escolha uma opção abaixo:                     ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [1] - Buscar livros pelo título                                ║");
        System.out.println("║ [2] - Listar livros registrados                                ║");
        System.out.println("║ [3] - Listar autores registrados                               ║");
        System.out.println("║ [4] - Listar autores vivos em um determinado ano               ║");
        System.out.println("║ [5] - Listar autores nascidos em determinado ano               ║");
        System.out.println("║ [6] - Listar autores por ano de sua morte                      ║");
        System.out.println("║ [7] - Listar livros em um determinado idioma                   ║");
        System.out.println("║                                                                ║");
        System.out.println("║ [0] - Sair                                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.print("Sua escolha: ");
    }

    private void salvarLivros(List<Livro> livros) {
        livros.forEach(livroRepository::save);
    }


    private void buscarLivrosPeloTitulo() {
        String baseURL = "https://gutendex.com/books?search=";

        try {
            System.out.println("----------------------------------------");
            System.out.println("Digite o título do livro: ");
            String titulo = leitura.nextLine();
            String endereco = baseURL + titulo.replace(" ", "%20");
            System.out.println("URL da API: " + endereco);

            String jsonResponse = consumoAPI.obterDados(endereco);
            // System.out.println("Resposta da API: " + jsonResponse); // Remover ou comentar para evitar poluir o console

            if (jsonResponse.isEmpty() || jsonResponse.equals("{\"count\":0,\"next\":null,\"previous\":null,\"results\":[]}")) {
                System.out.println("Não foi possível encontrar o livro buscado.");
                return;
            }

            JsonNode rootNode = converteDados.getObjectMapper().readTree(jsonResponse);
            JsonNode resultsNode = rootNode.path("results");

            if (resultsNode.isEmpty()) {
                System.out.println("Não foi possível encontrar o livro buscado.");
                return;
            }

            List<LivroDTO> livrosDTO = converteDados.getObjectMapper()
                    .readerForListOf(LivroDTO.class)
                    .readValue(resultsNode);

            // Verifica se o livro já existe no banco de dados antes de tentar salvar
            if (!livrosDTO.isEmpty()) {
                LivroDTO primeiroLivroEncontrado = livrosDTO.get(0);
                List<Livro> livrosExistentes = livroRepository.findByTitulo(primeiroLivroEncontrado.titulo());

                if (!livrosExistentes.isEmpty()) {
                    System.out.println("----------------------------------------");
                    System.out.println("O livro '" + primeiroLivroEncontrado.titulo() + "' já está registrado no banco de dados.");
                    // Exibir o livro já existente
                    System.out.println("Detalhes do livro já registrado:");
                    System.out.println(livrosExistentes.get(0).toString());
                    return; // Retorna para o menu principal
                }
            }

            List<Livro> livrosExistentes = livroRepository.findByTitulo(titulo);
            if (!livrosExistentes.isEmpty()) {
                System.out.println("----------------------------------------");
                System.out.println("Removendo livros duplicados já existentes no banco de dados...");
                for (Livro livroExistente : livrosExistentes) {
                    livrosDTO.removeIf(livroDTO -> livroExistente.getTitulo().equals(livroDTO.titulo()));
                }
            }

            if (!livrosDTO.isEmpty()) {
                System.out.println("----------------------------------------");
                System.out.println("Salvando novos livros encontrados...");
                List<Livro> novosLivros = livrosDTO.stream().map(Livro::new).collect(Collectors.toList());
                salvarLivros(novosLivros);
                System.out.println("Livros salvos com sucesso!");
            } else {
                System.out.println("----------------------------------------");
                System.out.println("Todos os livros já estão registrados no banco de dados.");
            }

            if (!livrosDTO.isEmpty()) {
                System.out.println("\n----------- Livros Encontrados -----------");
                Set<String> titulosExibidos = new HashSet<>();
                for (LivroDTO livro : livrosDTO) {
                    if (!titulosExibidos.contains(livro.titulo())) {
                        System.out.println(livro); // LivroDTO.toString() já formata bem
                        titulosExibidos.add(livro.titulo());
                    }
                }
                System.out.println("----------------------------------------");
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar livros: " + e.getMessage());
            System.out.println("----------------------------------------");
        }
    }


    private void listarLivrosRegistrados() {
        List<Livro> livros = livroRepository.findAll();
        System.out.println("\n----------- Livros Registrados -----------");
        if (livros.isEmpty()) {
            System.out.println("Nenhum livro registrado.");
        } else {
            livros.forEach(System.out::println);
        }
        System.out.println("----------------------------------------");
    }

    private void listarAutoresRegistrados() {
        List<Livro> livros = livroRepository.findAll();
        System.out.println("\n----------- Autores Registrados -----------");
        if (livros.isEmpty()) {
            System.out.println("Nenhum autor registrado.");
        } else {
            livros.stream()
                    .map(Livro::getAutor)
                    .distinct()
                    .forEach(autor -> {
                        String anoNascimento = autor.getAnoNascimento() != null ? autor.getAnoNascimento().toString() : "Desconhecido";
                        String anoFalecimento = autor.getAnoFalecimento() != null ? autor.getAnoFalecimento().toString() : "Desconhecido";
                        System.out.println("Autor: " + autor.getAutor() + " (Nasc: " + anoNascimento + ", Falec: " + anoFalecimento + ")");
                        System.out.println("----------------------------------------");
                    });
        }
        System.out.println("----------------------------------------");
    }

    private void listarAutoresVivos() {
        System.out.println("----------------------------------------");
        System.out.println("Digite o ano: ");
        Integer ano = leitura.nextInt();
        leitura.nextLine();

        Year year = Year.of(ano);

        List<Autor> autores = livroRepository.findAutoresVivos(year);
        System.out.println("\n----------- Autores Vivos em " + ano + " -----------");
        if (autores.isEmpty()) {
            System.out.println("Nenhum autor vivo encontrado no ano de " + ano + ".");
        } else {
            autores.forEach(autor -> {
                String anoNascimentoString = autor.getAnoNascimento() != null ? autor.getAnoNascimento().toString() : "Desconhecido";
                String anoFalecimentoString = autor.getAnoFalecimento() != null ? autor.getAnoFalecimento().toString() : "Desconhecido";
                System.out.println("Autor: " + autor.getAutor() + " (Nasc: " + anoNascimentoString + ", Falec: " + anoFalecimentoString + ")");
                System.out.println("----------------------------------------");
            });
        }
        System.out.println("----------------------------------------");
    }

    private void listarAutoresVivosRefinado() {
        System.out.println("----------------------------------------");
        System.out.println("Digite o ano: ");
        Integer ano = leitura.nextInt();
        leitura.nextLine();

        Year year = Year.of(ano);

        List<Autor> autores = livroRepository.findAutoresVivosRefinado(year); // Changed from findByAutoresRefinados
        System.out.println("\n----------- Autores Nascidos em " + ano + " -----------");
        if (autores.isEmpty()) {
            System.out.println("Nenhum autor nascido no ano de " + ano + " encontrado.");
        } else {
            autores.forEach(autor -> {
                String anoNascimentoString = autor.getAnoNascimento() != null ? autor.getAnoNascimento().toString() : "Desconhecido";
                String anoFalecimentoString = autor.getAnoFalecimento() != null ? autor.getAnoFalecimento().toString() : "Desconhecido";
                System.out.println("Autor: " + autor.getAutor() + " (Nasc: " + anoNascimentoString + ", Falec: " + anoFalecimentoString + ")");
                System.out.println("----------------------------------------");
            });
        }
        System.out.println("----------------------------------------");
    }

    private void listarAutoresPorAnoDeMorte() {
        System.out.println("----------------------------------------");
        System.out.println("Digite o ano: ");
        Integer ano = leitura.nextInt();
        leitura.nextLine();

        Year year = Year.of(ano);

        List<Autor> autores = livroRepository.findAutoresPorAnoDeMorte(year); // Changed from findyAutoresPorAnoDeMorte
        System.out.println("\n----------- Autores Falecidos em " + ano + " -----------");
        if (autores.isEmpty()) {
            System.out.println("Nenhum autor falecido no ano de " + ano + " encontrado.");
        } else {
            autores.forEach(autor -> {
                String anoNascimentoString = autor.getAnoNascimento() != null ? autor.getAnoNascimento().toString() : "Desconhecido";
                String anoFalecimentoString = autor.getAnoFalecimento() != null ? autor.getAnoFalecimento().toString() : "Desconhecido";
                System.out.println("Autor: " + autor.getAutor() + " (Nasc: " + anoNascimentoString + ", Falec: " + anoFalecimentoString + ")");
                System.out.println("----------------------------------------");
            });
        }
        System.out.println("----------------------------------------");
    }


    private void listarLivrosPorIdioma() {
        System.out.println("----------------------------------------");
        System.out.println("""
            Digite o idioma pretendido:
            Inglês (en)
            Português (pt)
            Espanhol (es)
            Francês (fr)
            Alemão (de)
            """);
        String idioma = leitura.nextLine();

        List<Livro> livros = livroRepository.findByIdioma(idioma);
        System.out.println("\n----------- Livros no idioma '" + idioma + "' -----------");
        if (livros.isEmpty()) {
            System.out.println("Nenhum livro encontrado no idioma especificado.");
        } else {
            livros.forEach(livro -> {
                String titulo = livro.getTitulo();
                String autor = livro.getAutor().getAutor();
                String idiomaLivro = livro.getIdioma();

                System.out.println("Título: " + titulo);
                System.out.println("Autor: " + autor);
                System.out.println("Idioma: " + idiomaLivro);
                System.out.println("----------------------------------------");
            });
        }
        System.out.println("----------------------------------------");
    }
}