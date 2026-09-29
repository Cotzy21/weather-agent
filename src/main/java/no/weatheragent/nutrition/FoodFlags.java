package no.weatheragent.nutrition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gjenkjenner allergener, diett-brudd og «misliker»-ord i et matvarenavn.
 * Ren og testbar, null API-kall.
 *
 * VIKTIG: dette er en navnebasert HJELP, ikke en garanti. Matvaretabellen har
 * ingen strukturert allergenliste vi kan stole på, og sammensatte retter kan
 * inneholde ting navnet ikke røper. UI-et sier derfor alltid «sjekk pakningen».
 *
 * Norske sammensatte ord: et nøkkelord treffer når et ORD i navnet starter
 * eller slutter med det («hvetemel» -> hvete, «grovbrød» -> brød), men ikke
 * midt i et ord («rostbiff» skal ikke treffe «ost»). Kjente falske treff
 * hoppes over PER ORD («kokosmelk» er ikke melk, «seigmenn» er ikke sei) - så
 * «Nøtteblanding med peanøtter» fortsatt treffer nøtter via første ord.
 * «Fri for»-merker («glutenfri») fjerner et allergen for hele navnet.
 */
public final class FoodFlags {

    /** De 14 allergenene EU krever merking av, med norske nøkkelord. */
    public enum Allergen {
        GLUTEN(List.of("hvete", "rug", "bygg", "havre", "spelt", "fullkorn", "brød", "pasta", "spagetti",
                        "makaroni", "nudler", "hvetemel", "rugmel", "byggmel", "sammalt", "kjeks", "couscous",
                        "bulgur", "pizza", "bolle", "kake", "lompe", "lefse", "tortilla", "baguett",
                        "rundstykke", "semule", "seitan"),
                List.of("risnudler", "glassnudler", "rispasta", "maismel", "potetmel", "mandelmel",
                        "kokosmel", "rismel", "kakao"),
                List.of("glutenfri")),
        MELK(List.of("melk", "ost", "yoghurt", "jogurt", "fløte", "smør", "rømme", "kesam", "skyr", "iskrem",
                        "kefir", "cottage", "myse", "prim", "laktose", "meieri", "cheese", "mozzarella",
                        "parmesan", "feta", "mascarpone"),
                List.of("kokosmelk", "havremelk", "soyamelk", "mandelmelk", "rismelk", "plantedrikk",
                        "peanøttsmør", "nøttesmør", "mandelsmør", "kakaosmør"),
                List.of()),
        EGG(List.of("egg", "majones", "eggehvite", "eggeplomme", "omelett", "eggerøre"),
                List.of("legg"), List.of()),
        NOTTER(List.of("mandel", "mandler", "valnøtt", "hasselnøtt", "cashew", "pistasj", "pekan",
                        "paranøtt", "macadamia", "nøtt", "nøtter", "marsipan", "nougat"),
                List.of("peanøtt", "kokosnøtt", "muskatnøtt", "jordnøtt"), List.of()),
        PEANOTTER(List.of("peanøtt", "peanøtter", "jordnøtt"), List.of(), List.of()),
        FISK(List.of("fisk", "laks", "torsk", "sei", "makrell", "sild", "tunfisk", "ørret", "hyse", "kveite",
                        "rødspette", "sardin", "ansjos", "kaviar", "tran", "lutefisk", "klippfisk"),
                List.of("seig", "seitan", "tranebær"), List.of()),
        SKALLDYR(List.of("reke", "reker", "krabbe", "hummer", "kreps", "scampi", "skalldyr"),
                List.of(), List.of()),
        BLOTDYR(List.of("blåskjell", "østers", "blekksprut", "akkar", "kamskjell", "skjell", "snegl"),
                List.of(), List.of()),
        SOYA(List.of("soya", "tofu", "edamame", "tempeh", "miso"), List.of(), List.of()),
        SELLERI(List.of("selleri"), List.of(), List.of()),
        SENNEP(List.of("sennep"), List.of(), List.of()),
        SESAM(List.of("sesam", "tahini", "hummus"), List.of(), List.of()),
        LUPIN(List.of("lupin"), List.of(), List.of()),
        SULFITT(List.of("vin", "rosin", "rosiner", "sulfitt", "tørket aprikos"),
                List.of("vindruer", "vinranker"), List.of());

        private final List<String> keywords;
        private final List<String> wordExceptions;
        private final List<String> freeFromMarkers;

        Allergen(List<String> keywords, List<String> wordExceptions, List<String> freeFromMarkers) {
            this.keywords = keywords;
            this.wordExceptions = wordExceptions;
            this.freeFromMarkers = freeFromMarkers;
        }

        public boolean matches(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (freeFromMarkers.stream().anyMatch(lower::contains)) {
                return false;
            }
            return matchesAny(lower, keywords, wordExceptions);
        }
    }

    public enum Diet {
        ALT, VEGETAR, PESCETAR, VEGAN
    }

    private static final List<String> MEAT = List.of(
            "kjøtt", "kylling", "høne", "fjørfe", "svin", "storfe", "okse", "kalv", "biff", "lam", "får",
            "kalkun", "and", "hjort", "elg", "rein", "rådyr", "vilt", "bacon", "skinke", "pølse", "salami",
            "postei", "lever", "karbonade", "kotelett", "entrecote", "indrefilet", "ytrefilet", "fårikål",
            "ribbe", "medister", "kebab", "burger", "kjøttdeig", "kjøttkake", "pinnekjøtt", "gelatin");
    private static final List<String> MEAT_EXCEPTIONS = List.of(
            "vegetar", "vegansk", "vegan", "soya", "plantebasert", "sopp", "linse", "bønne", "seitan");
    private static final List<String> VEGAN_EXTRA = List.of("honning");

    private FoodFlags() {
    }

    /**
     * Advarsler for en matvare mot en brukerprofil, som koder frontenden
     * oversetter: "ALLERGEN:MELK", "DIETT", "MISLIKER". Tom liste = ingen funn.
     */
    public static List<String> warnings(String foodName, DietProfile profile) {
        String name = foodName == null ? "" : foodName.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        // Fast rekkefølge (enum-rekkefølge), så svaret er stabilt.
        for (Allergen a : Allergen.values()) {
            if (profile.allergies().contains(a) && a.matches(name)) {
                out.add("ALLERGEN:" + a.name());
            }
        }
        if (violatesDiet(name, profile.diet())) {
            out.add("DIETT");
        }
        for (String word : profile.dislikes()) {
            String w = word.toLowerCase(Locale.ROOT).trim();
            if (!w.isEmpty() && name.contains(w)) {
                out.add("MISLIKER");
                break;
            }
        }
        return out;
    }

    /** True hvis maten er grei for profilen (ingen advarsler). */
    public static boolean allowed(String foodName, DietProfile profile) {
        return warnings(foodName, profile).isEmpty();
    }

    static boolean violatesDiet(String name, Diet diet) {
        if (diet == Diet.ALT) {
            return false;
        }
        boolean meat = matchesAny(name, MEAT, MEAT_EXCEPTIONS);
        boolean seafood = Allergen.FISK.matches(name) || Allergen.SKALLDYR.matches(name)
                || Allergen.BLOTDYR.matches(name);
        return switch (diet) {
            case ALT -> false;
            case PESCETAR -> meat;
            case VEGETAR -> meat || seafood;
            case VEGAN -> meat || seafood || Allergen.MELK.matches(name) || Allergen.EGG.matches(name)
                    || matchesAny(name, VEGAN_EXTRA, List.of());
        };
    }

    /**
     * Treff hvis et ord i navnet starter/slutter med et nøkkelord (ord som
     * inneholder et unntak hoppes over), eller et nøkkelord med mellomrom står
     * i navnet.
     */
    static boolean matchesAny(String name, List<String> keywords, List<String> wordExceptions) {
        String lower = name.toLowerCase(Locale.ROOT);
        List<String> words = Arrays.stream(lower.split("[^\\p{L}]+"))
                .filter(w -> !w.isEmpty())
                .filter(w -> wordExceptions.stream().noneMatch(w::contains))
                .toList();
        for (String kw : keywords) {
            if (kw.contains(" ")) {
                if (lower.contains(kw)) {
                    return true;
                }
                continue;
            }
            for (String w : words) {
                if (w.startsWith(kw) || w.endsWith(kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Allergen-kodene brukeren kan velge, for validering av innkommende data. */
    public static Set<String> allergenCodes() {
        return Arrays.stream(Allergen.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
