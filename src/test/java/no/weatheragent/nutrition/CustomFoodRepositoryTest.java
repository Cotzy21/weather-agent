package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kjører repository-spørringene (JPQL med CASE-sortering, LIKE og paging) mot
 * en ekte in-memory database, så feil i spørringene fanges her og ikke først
 * ved oppstart i prod. Flyway er av (migrasjonene er Postgres-spesifikke);
 * Hibernate lager tabellen fra entiteten. H2 klarer ikke lage alle de ANDRE
 * tabellene (jsonb m.m.) - det er ufarlig her, så de advarslene dempes.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:customfoods;MODE=PostgreSQL",
        "logging.level.org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl=OFF",
})
class CustomFoodRepositoryTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Autowired
    private CustomFoodRepository repository;

    private CustomFood food(UUID owner, String name, String barcode, boolean isPublic) {
        return repository.save(new CustomFood(owner, name, null, barcode, 100, 10, 5, 10, null, null, isPublic));
    }

    @Test
    void searchShowsOwnAndPublicButNotOthersPrivateFoods() {
        food(OTHER, "Proteinbar delt", null, true);
        food(ME, "Proteinshake min", null, false);
        food(OTHER, "Proteinpudding hemmelig", null, false);

        List<CustomFood> hits = repository.searchVisible(ME, "PROTEIN", PageRequest.of(0, 10));

        assertEquals(List.of("Proteinshake min", "Proteinbar delt"),
                hits.stream().map(CustomFood::getName).toList()); // egne først, ukjenslig for store/små bokstaver
    }

    @Test
    void searchRespectsThePageSize() {
        for (int i = 0; i < 5; i++) {
            food(ME, "Havre " + i, null, false);
        }

        assertEquals(3, repository.searchVisible(ME, "havre", PageRequest.of(0, 3)).size());
    }

    @Test
    void barcodeLookupPrefersOwnFoodAndHidesOthersPrivate() {
        food(OTHER, "Delt versjon", "7038010009457", true);
        food(ME, "Min versjon", "7038010009457", false);
        food(OTHER, "Hemmelig", "7000000000001", false);

        assertEquals("Min versjon",
                repository.findVisibleByBarcode(ME, "7038010009457").getFirst().getName());
        assertTrue(repository.findVisibleByBarcode(ME, "7000000000001").isEmpty());
    }
}
