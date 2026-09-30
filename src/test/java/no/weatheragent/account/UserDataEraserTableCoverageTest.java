package no.weatheragent.account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sikringsnett for kontosletting: hver JPA-entitet (= tabell med data) må stå i {@link UserDataEraser}. Legger du til
 * en ny entitet uten å ta den med der, feiler denne testen, så vi aldri lagrer brukerdata vi ikke kan slette.
 * Er tabellen bevisst uten brukerdata (f.eks. en delt oppslagstabell), legg den i {@code SHARED_TABLES} under.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:eraserCoverage;MODE=PostgreSQL",
        "logging.level.org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl=OFF",
})
class UserDataEraserTableCoverageTest {

    /** Tabeller uten brukerdata. Tom i dag. */
    private static final Set<String> SHARED_TABLES = Set.of();

    @Autowired
    private EntityManager entityManager;

    @Test
    void everyEntityTableIsCoveredByTheEraser() {
        Set<String> entityTables = new TreeSet<>();
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            Table table = entity.getJavaType().getAnnotation(Table.class);
            entityTables.add(table != null ? table.name() : entity.getName().toLowerCase());
        }
        entityTables.removeAll(SHARED_TABLES);

        assertEquals(new TreeSet<>(UserDataEraser.coveredTables()), entityTables,
                "UserDataEraser.OWNED_TABLES og entitetene i appen må være like");
    }
}
