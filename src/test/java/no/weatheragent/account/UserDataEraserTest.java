package no.weatheragent.account;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UserDataEraserTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserDataEraser eraser = new UserDataEraser(jdbc);

    @Test
    void deletesFromEveryOwnedTableScopedToTheUser() {
        eraser.eraseAll(USER);

        for (UserDataEraser.OwnedTable t : UserDataEraser.OWNED_TABLES) {
            verify(jdbc).update("delete from " + t.table() + " where " + t.ownerColumn() + " = ?", USER);
        }
    }

    @Test
    void habitLogsAreDeletedBeforeTheirHabitsBecauseTheyHaveNoUserColumn() {
        eraser.eraseAll(USER);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).update(
                "delete from habit_logs where habit_id in (select id from habits where user_id = ?)", USER);
        order.verify(jdbc).update("delete from habits where user_id = ?", USER);
    }

    @Test
    void doesNothingElseThanTheKnownDeletes() {
        eraser.eraseAll(USER);

        verify(jdbc, times(1 + UserDataEraser.OWNED_TABLES.size())).update(anyString(), eq(USER));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void returnsTheTotalNumberOfDeletedRows() {
        when(jdbc.update(anyString(), eq(USER))).thenReturn(2);

        assertEquals(2 * (1 + UserDataEraser.OWNED_TABLES.size()), eraser.eraseAll(USER));
    }

    @Test
    void neverBuildsSqlFromAnythingButTheFixedTableList() {
        // Tabell- og kolonnenavn settes rett inn i SQL-en: de må være rene identifikatorer.
        for (UserDataEraser.OwnedTable t : UserDataEraser.OWNED_TABLES) {
            org.junit.jupiter.api.Assertions.assertTrue(t.table().matches("[a-z_]+"), t.table());
            org.junit.jupiter.api.Assertions.assertTrue(t.ownerColumn().matches("[a-z_]+"), t.ownerColumn());
        }
    }
}
