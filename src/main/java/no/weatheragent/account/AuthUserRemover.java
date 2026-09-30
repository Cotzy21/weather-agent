package no.weatheragent.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fjerner selve innloggingen (e-post og passord-hash hos Supabase), slik at kontoen er helt borte og ikke bare
 * tømt for data. Supabase lagrer brukerne i tabellen {@code auth.users}, og backend kobler til som databaseeieren
 * (se SECURITY.md), så vi kan slette raden direkte uten en egen service-nøkkel.
 *
 * Dette er «best effort» og kjører UTENFOR sletteransaksjonen: feiler det (ingen {@code auth}-skjema i lokal
 * Postgres, manglende rettigheter) er brukerdataene allerede slettet, og vi svarer {@code false} så appen kan si
 * at innloggingen må fjernes på en annen måte. Feilen logges uten personopplysninger.
 */
@Component
public class AuthUserRemover {

    private static final Logger log = LoggerFactory.getLogger(AuthUserRemover.class);

    private final JdbcTemplate jdbc;

    public AuthUserRemover(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True hvis innloggingen ble slettet. */
    public boolean remove(UUID userId) {
        try {
            Boolean hasAuthTable = jdbc.queryForObject("select to_regclass('auth.users') is not null", Boolean.class);
            if (!Boolean.TRUE.equals(hasAuthTable)) {
                return false;
            }
            return jdbc.update("delete from auth.users where id = ?", userId) > 0;
        } catch (DataAccessException e) {
            log.warn("Kunne ikke slette innloggingen i Supabase ({}). Brukerdataene er slettet.", e.getClass().getSimpleName());
            return false;
        }
    }
}
