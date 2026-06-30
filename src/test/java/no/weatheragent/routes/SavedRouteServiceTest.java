package no.weatheragent.routes;

import no.weatheragent.route.RoutePoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedRouteServiceTest {

    private final SavedRouteRepository repo = mock(SavedRouteRepository.class);
    private final SavedRouteService service = new SavedRouteService(repo);
    private final UUID user = UUID.randomUUID();

    @Test
    void saveDelegatesToRepository() {
        service.save(user, "Tur", 5, 100, List.of(new RoutePoint(60, 10)));
        verify(repo).save(any(SavedRoute.class));
    }

    @Test
    void deleteReturnsTrueWhenRowRemoved() {
        when(repo.deleteByIdAndUserId(any(), eq(user))).thenReturn(1L);
        assertTrue(service.delete(UUID.randomUUID(), user));
    }

    @Test
    void deleteReturnsFalseWhenNothingRemoved() {
        when(repo.deleteByIdAndUserId(any(), eq(user))).thenReturn(0L);
        assertFalse(service.delete(UUID.randomUUID(), user));
    }

    @Test
    void listScopesToUser() {
        service.listFor(user);
        verify(repo).findByUserIdOrderByCreatedAtDesc(user);
    }
}
