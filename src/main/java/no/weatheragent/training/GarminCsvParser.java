package no.weatheragent.training;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser Garmin Connect sin aktivitets-CSV (Aktiviteter -> Alle aktiviteter ->
 * «Eksporter CSV»). Ren og testbar uten nettverk, som de andre parserne.
 *
 * Tåler både norsk og engelsk konto-språk (kolonnenavn OG tallformat):
 * norske tall bruker desimalkomma («5,02»), engelske bruker tusenskilletegn
 * («1,234»), og manglende verdier er «--». Rader vi ikke forstår hoppes over -
 * en delvis import er mer verdt enn en feilmelding.
 */
public final class GarminCsvParser {

    private GarminCsvParser() {
    }

    public static List<GarminActivity> parse(String csv) {
        List<List<String>> rows = tokenize(csv);
        if (rows.size() < 2) {
            return List.of();
        }

        List<String> header = rows.getFirst().stream()
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .toList();
        int typeCol = find(header, "aktivitetstype", "activity type");
        int dateCol = findExact(header, "dato", "date");
        int titleCol = findExact(header, "tittel", "title");
        int distCol = findExact(header, "distanse", "distance");
        int kcalCol = findExact(header, "kalorier", "calories");
        int timeCol = findExact(header, "tid", "time");
        int ascentCol = find(header, "stigning", "ascent");

        if (typeCol < 0 || dateCol < 0 || titleCol < 0) {
            return List.of(); // ikke en aktivitets-CSV fra Garmin
        }

        List<GarminActivity> result = new ArrayList<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            LocalDate date = date(get(row, dateCol));
            String title = get(row, titleCol).trim();
            if (date == null || title.isEmpty()) {
                continue;
            }
            String type = mapType(get(row, typeCol));
            Double distance = number(get(row, distCol));
            // Bassengsvømming oppgis i meter i Garmin-CSV-en, ellers km.
            if (distance != null && "SVØMMING".equals(type) && distance > 50) {
                distance = distance / 1000.0;
            }
            result.add(new GarminActivity(
                    date, title, type,
                    distance,
                    minutes(get(row, timeCol)),
                    number(get(row, ascentCol)),
                    number(get(row, kcalCol))));
        }
        return result;
    }

    /** Garmin-aktivitetstype -> appens økt-type. Ukjent blir FRISTIL. */
    static String mapType(String raw) {
        String t = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (t.contains("løping") || t.contains("running") || t.contains("treadmill")) return "LØPING";
        if (t.contains("styrke") || t.contains("strength")) return "STYRKE";
        if (t.contains("svøm") || t.contains("swim")) return "SVØMMING";
        if (t.contains("sykling") || t.contains("sykkel") || t.contains("cycling")
                || t.contains("biking") || t.contains("ride")) return "SYKKEL";
        if (t.contains("vandring") || t.contains("hiking") || t.contains("walking")
                || t.contains("gange") || t.contains("gåing")) return "HIKING";
        if (t.contains("klatring") || t.contains("bouldering") || t.contains("climb")) return "BULDRING";
        return "FRISTIL";
    }

    /** «2026-07-01 18:23:45» -> datoen; null når feltet ikke er en dato. */
    private static LocalDate date(String raw) {
        String s = raw.trim();
        if (s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** «00:28:15» eller «28:15» -> minutter; null når feltet mangler. */
    private static Double minutes(String raw) {
        String s = raw.trim().replace("\"", "");
        if (s.isEmpty() || s.startsWith("--")) {
            return null;
        }
        String[] parts = s.split(":");
        try {
            if (parts.length == 3) {
                return Double.parseDouble(parts[0]) * 60
                        + Double.parseDouble(parts[1])
                        + Double.parseDouble(parts[2]) / 60.0;
            }
            if (parts.length == 2) {
                return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]) / 60.0;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /**
     * Tall i begge formater: engelsk «1,234.5» (komma = tusen) og norsk «5,02»
     * (komma = desimal). «--» og tomt = null.
     */
    private static Double number(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(" ", "").replace(" ", "");
        if (s.isEmpty() || s.startsWith("--")) {
            return null;
        }
        if (s.matches("\\d{1,3}(,\\d{3})+(\\.\\d+)?")) {
            s = s.replace(",", ""); // engelsk tusenskilletegn
        } else {
            s = s.replace(',', '.'); // norsk desimalkomma
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String get(List<String> row, int col) {
        return col >= 0 && col < row.size() ? row.get(col) : "";
    }

    private static int findExact(List<String> header, String... names) {
        for (String name : names) {
            int i = header.indexOf(name);
            if (i >= 0) {
                return i;
            }
        }
        return find(header, names);
    }

    private static int find(List<String> header, String... needles) {
        for (int i = 0; i < header.size(); i++) {
            for (String needle : needles) {
                if (header.get(i).contains(needle)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Enkel CSV-tokenizer med korrekt håndtering av anførselstegn og "" som escape. */
    private static List<List<String>> tokenize(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (row.stream().anyMatch(f -> !f.isBlank())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        row.add(field.toString());
        if (row.stream().anyMatch(f -> !f.isBlank())) {
            rows.add(row);
        }
        return rows;
    }
}
