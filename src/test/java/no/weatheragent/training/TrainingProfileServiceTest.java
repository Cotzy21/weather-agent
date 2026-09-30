package no.weatheragent.training;

import no.weatheragent.interpret.LlmTier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingProfileServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final TrainingProfileRepository repo = mock(TrainingProfileRepository.class);
    private final TrainingProfileService service = new TrainingProfileService(repo);

    private static TrainingProfileData profile(String level, String technique, String injuries) {
        return new TrainingProfileData(level, 24, 3, "MUSCLE", "GYM", 4, 60, technique, "BRIEF",
                injuries, "", "sterk overkropp", "svake bein");
    }

    @Test
    void beginnersAndCautiousUsersGetTheStrongestModel() {
        assertEquals(LlmTier.PRO, TrainingProfileService.tierOf(profile("BEGINNER", "HIGH", "")));
        assertEquals(LlmTier.PRO, TrainingProfileService.tierOf(profile("NOVICE", "HIGH", "")));
        assertEquals(LlmTier.PRO, TrainingProfileService.tierOf(profile("ADVANCED", "LOW", "")));
        assertEquals(LlmTier.PRO, TrainingProfileService.tierOf(profile("ADVANCED", "HIGH", "vondt kne")));
    }

    @Test
    void experiencedUsersAndUsersWithoutProfileGetSmart() {
        assertEquals(LlmTier.SMART, TrainingProfileService.tierOf(profile("INTERMEDIATE", "MEDIUM", "")));
        assertEquals(LlmTier.SMART, TrainingProfileService.tierOf(profile("ADVANCED", "HIGH", "")));
        when(repo.findById(USER)).thenReturn(Optional.empty());
        assertEquals(LlmTier.SMART, service.tierFor(USER));
    }

    @Test
    void promptContextDescribesUserAndAdaptsToLevel() {
        String beginner = TrainingProfileService.describe(profile("BEGINNER", "LOW", "vondt kne"));
        assertTrue(beginner.contains("nybegynner"));
        assertTrue(beginner.contains("Skader/begrensninger: vondt kne"));
        assertTrue(beginner.contains("Svakheter/prioriteres: svake bein"));
        assertTrue(beginner.contains("Tilpasning til nybegynner"));

        String advanced = TrainingProfileService.describe(profile("ADVANCED", "HIGH", ""));
        assertTrue(advanced.contains("Tilpasning til avansert"));
        assertTrue(!advanced.contains("Skader"));
    }

    @Test
    void savesNormalisedProfile() {
        TrainingProfileData in = new TrainingProfileData(" beginner ", 0, 0, "health", "gym", 3, 45, "low", "detailed",
                "  vondt   kne ", null, null, null);

        TrainingProfileData saved = service.save(USER, in);

        assertEquals("BEGINNER", saved.experienceLevel());
        assertEquals("vondt kne", saved.injuries());
        assertEquals("", saved.background());
        verify(repo).save(org.mockito.ArgumentMatchers.any(TrainingProfile.class));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> service.save(USER, profile("GURU", "HIGH", "")));
        assertThrows(IllegalArgumentException.class, () -> service.save(USER,
                new TrainingProfileData("BEGINNER", 0, 0, "HEALTH", "GYM", 9, 45, "LOW", "BRIEF", "", "", "", "")));
        assertThrows(IllegalArgumentException.class, () -> service.save(USER,
                profile("BEGINNER", "LOW", "x".repeat(301))));
    }
}
