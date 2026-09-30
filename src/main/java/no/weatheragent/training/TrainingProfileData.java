package no.weatheragent.training;

/**
 * Onboarding-svarene. Brukes både som inn- og utdata (API) og som verdiobjekt i tjenesten.
 *
 * @param experienceLevel BEGINNER | NOVICE | INTERMEDIATE | ADVANCED
 * @param trainingMonths  hvor lenge brukeren har trent totalt (0 = aldri)
 * @param sessionsPerWeek hvor mange økter i uka de faktisk har tatt siste 3 måneder
 * @param goal            STRENGTH | MUSCLE | FAT_LOSS | ENDURANCE | HEALTH
 * @param equipment       GYM | HOME_WEIGHTS | BODYWEIGHT
 * @param daysPerWeek     dager i uka brukeren vil trene
 * @param sessionMinutes  minutter per økt
 * @param technique       LOW | MEDIUM | HIGH: hvor trygg brukeren er på teknikken i de store løftene
 * @param explanationStyle DETAILED | BRIEF
 */
public record TrainingProfileData(
        String experienceLevel,
        int trainingMonths,
        int sessionsPerWeek,
        String goal,
        String equipment,
        int daysPerWeek,
        int sessionMinutes,
        String technique,
        String explanationStyle,
        String injuries,
        String background,
        String strengths,
        String weaknesses) {
}
