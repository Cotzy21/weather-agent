package no.weatheragent.interpret;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Opt-in demo: tolk ett fritekst-spørsmål med den lokale LLM-en og skriv ut
 * resultatet. Kjører KUN når {@code demo.interpret=true}, slik at vanlig
 * oppstart ikke krever at LM Studio er i gang.
 *
 * Kjør (PowerShell), der ordene etter flagget blir spørsmålet:
 * <pre>
 * mvn spring-boot:run "-Dspring-boot.run.arguments=--demo.interpret=true hvor i Møre og Romsdal blir det best vær i helga"
 * </pre>
 */
@Component
@Order(1) // kjør før den eksisterende DemoRunner, så tolkningen vises øverst
@ConditionalOnProperty(name = "demo.interpret", havingValue = "true")
public class InterpretDemoRunner implements CommandLineRunner {

    private static final String DEFAULT_QUERY =
            "hvor i Møre og Romsdal blir det best vær i helga";

    private final QueryInterpreter interpreter;

    public InterpretDemoRunner(QueryInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override
    public void run(String... args) {
        String query = query(args);

        System.out.println("\n=== Tolkning (lokal LLM) ===");
        System.out.println("Spørsmål : " + query);

        Interpretation r = interpreter.interpret(query);

        System.out.println("Region   : " + (r.hasRegion() ? r.region() : "(ukjent)"));
        System.out.println("Datoer   : " + r.dates().from() + " -> " + r.dates().to()
                + "  (" + r.dates().days().size() + " dag(er))");
        System.out.println("Turtype  : " + r.tripType());
        System.out.println();
    }

    /** Alle ord som ikke er --flagg, slått sammen til spørsmålet. */
    private static String query(String[] args) {
        String joined = Arrays.stream(args)
                .filter(arg -> !arg.startsWith("--"))
                .collect(Collectors.joining(" "))
                .trim();
        return joined.isEmpty() ? DEFAULT_QUERY : joined;
    }
}
