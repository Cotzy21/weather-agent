package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mot en ekte in-memory database, så JPQL-en (gruppering, projeksjon) og slettingene er prøvd før prod. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:foodreports;MODE=PostgreSQL",
        "logging.level.org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl=OFF",
})
@Import(FoodReportService.class)
class FoodReportServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Autowired
    private FoodReportService service;
    @Autowired
    private FoodReportRepository reports;
    @Autowired
    private CustomFoodRepository foods;

    private CustomFood food(UUID owner, String name, boolean isPublic) {
        return foods.save(new CustomFood(owner, name, null, null, 100, 10, 5, 10, null, null, isPublic));
    }

    @Test
    void aUserCanReportAnotherUsersPublicFood() {
        CustomFood shared = food(OWNER, "Proteinbar", true);

        service.report(ALICE, shared.getId(), ReportReason.WRONG_VALUES, "  Feil kcal  ");

        List<FoodReport> stored = reports.findAll();
        assertEquals(1, stored.size());
        assertEquals(ReportReason.WRONG_VALUES, stored.get(0).getReason());
        assertEquals("Feil kcal", stored.get(0).getNote()); // trimmet
    }

    @Test
    void reportingTheSameFoodTwiceIsHarmlessAndDoesNotAddAnotherReport() {
        CustomFood shared = food(OWNER, "Proteinbar", true);

        service.report(ALICE, shared.getId(), ReportReason.SPAM, null);
        service.report(ALICE, shared.getId(), ReportReason.OTHER, "igjen");

        assertEquals(1, reports.count());
    }

    @Test
    void youCannotReportYourOwnFood() {
        CustomFood mine = food(ALICE, "Min shake", true);

        assertThrows(IllegalArgumentException.class, () -> service.report(ALICE, mine.getId(), ReportReason.SPAM, null));
        assertEquals(0, reports.count());
    }

    @Test
    void privateAndUnknownFoodsCannotBeReportedSoTheyCannotBeProbed() {
        CustomFood hidden = food(OWNER, "Hemmelig", false);

        assertThrows(IllegalArgumentException.class, () -> service.report(ALICE, hidden.getId(), ReportReason.SPAM, null));
        assertThrows(IllegalArgumentException.class, () -> service.report(ALICE, UUID.randomUUID(), ReportReason.SPAM, null));
        assertThrows(IllegalArgumentException.class, () -> service.report(ALICE, hidden.getId(), null, null));
        assertEquals(0, reports.count());
    }

    @Test
    void aUserCannotFloodTheQueueWithReports() {
        for (int i = 0; i < FoodReportService.MAX_REPORTS_PER_USER; i++) {
            service.report(ALICE, food(OWNER, "Vare " + i, true).getId(), ReportReason.SPAM, null);
        }
        CustomFood one = food(OWNER, "En til", true);

        assertThrows(IllegalArgumentException.class, () -> service.report(ALICE, one.getId(), ReportReason.SPAM, null));
    }

    @Test
    void longNotesAreCutToTheColumnSize() {
        CustomFood shared = food(OWNER, "Proteinbar", true);

        service.report(ALICE, shared.getId(), ReportReason.OTHER, "x".repeat(500));

        assertEquals(300, reports.findAll().get(0).getNote().length());
    }

    @Test
    void adminSeesMostReportedFirstWithReasonsAndNotes() {
        CustomFood popular = food(OWNER, "Populært galt", true);
        CustomFood single = food(OWNER, "Én rapport", true);
        service.report(ALICE, popular.getId(), ReportReason.WRONG_VALUES, "kcal stemmer ikke");
        service.report(BOB, popular.getId(), ReportReason.SPAM, null);
        service.report(BOB, single.getId(), ReportReason.INAPPROPRIATE, null);

        List<FoodReportService.ReportedFood> listed = service.listReported();

        assertEquals(List.of("Populært galt", "Én rapport"), listed.stream().map(r -> r.food().getName()).toList());
        FoodReportService.ReportedFood first = listed.get(0);
        assertEquals(2, first.reports());
        assertEquals(1L, first.reasons().get(ReportReason.WRONG_VALUES));
        assertEquals(1L, first.reasons().get(ReportReason.SPAM));
        assertEquals(List.of("kcal stemmer ikke"), first.notes());
    }

    @Test
    void deletingTheFoodRemovesItsReportsAndOnlyThose() {
        CustomFood bad = food(OWNER, "Dårlig", true);
        CustomFood other = food(OWNER, "Annen", true);
        service.report(ALICE, bad.getId(), ReportReason.SPAM, null);
        service.report(ALICE, other.getId(), ReportReason.SPAM, null);

        assertTrue(service.deleteFood(bad.getId()));

        assertFalse(foods.existsById(bad.getId()));
        assertEquals(List.of(other.getId()), reports.findAll().stream().map(FoodReport::getFoodId).toList());
        assertFalse(service.deleteFood(bad.getId())); // finnes ikke lenger
    }

    @Test
    void dismissingKeepsTheFoodAndClearsItsReports() {
        CustomFood fine = food(OWNER, "Helt greit", true);
        service.report(ALICE, fine.getId(), ReportReason.OTHER, null);
        service.report(BOB, fine.getId(), ReportReason.OTHER, null);

        assertEquals(2, service.dismiss(fine.getId()));

        assertTrue(foods.existsById(fine.getId()));
        assertEquals(0, reports.count());
        assertTrue(service.listReported().isEmpty());
    }
}
