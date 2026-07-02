package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Brukerens lagrede treningsplaner: lagre (typisk fra et AI-forslag man likte),
 * liste og slette. Scoped til eieren, samme mønster som øktene.
 */
@Service
public class TrainingPlanService {

    /** Rimelig tak så ingen samler tusenvis av planer. */
    static final int MAX_PLANS = 30;

    private final TrainingPlanRepository repository;

    public TrainingPlanService(TrainingPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TrainingPlan save(UUID userId, String title, String type, JsonNode content, String rationale) {
        if (repository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_PLANS) {
            throw new IllegalArgumentException(
                    "Du har allerede " + MAX_PLANS + " planer - slett en gammel først.");
        }
        return repository.save(new TrainingPlan(userId, title, type, content, rationale));
    }

    @Transactional(readOnly = true)
    public List<TrainingPlan> listFor(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }
}
