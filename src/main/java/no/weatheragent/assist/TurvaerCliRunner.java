package no.weatheragent.assist;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tur-assistent i terminalen. To moduser, begge bak {@code --cli=true}:
 * <ul>
 *   <li>Med spørsmål som argument: kjør én gang og avslutt.<br>
 *       {@code ... --cli=true hvor er det finest vær i Rogaland neste uke}</li>
 *   <li>Uten: interaktiv løkke der du skriver spørsmål til du skriver 'exit'.</li>
 * </ul>
 *
 * Krever LM Studio + nett (Overpass/MET). Stdin fungerer når du kjører fra
 * IntelliJ (grønn kjør-knapp) eller en pakket jar ({@code java -jar ...}), men
 * ikke alltid via {@code spring-boot:run}. En feil (f.eks. Overpass 504) i
 * løkka stopper ikke økta - den skrives ut, og du kan prøve igjen.
 */
@Component
@ConditionalOnProperty(name = "cli", havingValue = "true")
public class TurvaerCliRunner implements CommandLineRunner {

    private final TurvaerService service;

    public TurvaerCliRunner(TurvaerService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws IOException {
        String oneShot = nonFlagArgs(args);
        if (!oneShot.isEmpty()) {
            answer(oneShot);
            return;
        }
        loop();
    }

    private void loop() throws IOException {
        System.out.println("\n=== Turvær-assistent ===");
        System.out.println("Skriv et spørsmål, f.eks. \"hvor er det finest vær i Rogaland neste uke\".");
        System.out.println("Tom linje eller 'exit' avslutter.\n");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("> ");
            String line = in.readLine();
            if (line == null) {
                break; // EOF (Ctrl+D / Ctrl+Z)
            }
            line = line.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                break;
            }
            answer(line);
            System.out.println();
        }
        System.out.println("Ha det!");
    }

    /** Kjør ett spørsmål gjennom hele kjeden og skriv ut svaret (eller feilen). */
    private void answer(String query) {
        try {
            System.out.println(TurResultPrinter.format(service.finnBesteVaer(query)));
        } catch (Exception e) {
            System.out.println("Beklager, noe gikk galt: " + e.getMessage());
        }
    }

    /** Alle ord som ikke er --flagg, slått sammen til ett spørsmål. */
    private static String nonFlagArgs(String[] args) {
        return Arrays.stream(args)
                .filter(arg -> !arg.startsWith("--"))
                .collect(Collectors.joining(" "))
                .trim();
    }
}
