package no.weatheragent.training;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingPlanServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final TrainingPlanRepository repository = mock(TrainingPlanRepository.class);
    private final TrainingPlanService service = new TrainingPlanService(repository);

    @Test
    void savesPlanForUser() {
        when(repository.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlan plan = service.save(USER, "Push-dag", "STYRKE",
                JsonNodeFactory.instance.objectNode(), "Progressiv overload på benk.");

        assertEquals(USER, plan.getUserId());
        assertEquals("Push-dag", plan.getTitle());
    }

    @Test
    void refusesWhenPlanLimitReached() {
        when(repository.findByUserIdOrderByCreatedAtDesc(USER))
                .thenReturn(Collections.nCopies(TrainingPlanService.MAX_PLANS,
                        new TrainingPlan(USER, "x", "STYRKE", JsonNodeFactory.instance.objectNode(), null)));

        assertThrows(IllegalArgumentException.class, () -> service.save(
                USER, "En til", "STYRKE", JsonNodeFactory.instance.objectNode(), null));
    }
}
