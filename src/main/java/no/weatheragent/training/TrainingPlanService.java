package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Brukerens lagrede økter (maler): lagre (fra byggeren eller et AI-forslag man likte), liste og slette. En mal er aldri en
 * gjennomført økt; den logges først når brukeren starter den. Scoped til eieren, samme mønster som øktene.
 */
@Service
public class TrainingPlanService {

    /**
     * Rimelig tak så ingen samler tusenvis av maler. Lagrede økter er malene man starter fra (▶ Start), og de samler seg
     * over tid (Push A/B, Pull, Bein, løpeøkter, turer …), så taket er romslig.
     */
    static final int MAX_PLANS = 100;

    private final TrainingPlanRepository repository;

    public TrainingPlanService(TrainingPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TrainingPlan save(UUID userId, String title, String type, JsonNode content, String rationale) {
        if (repository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_PLANS) {
            throw new IllegalArgumentException(
                    "Du har allerede " + MAX_PLANS + " lagrede økter - slett en gammel først.");
        }
        return repository.save(new TrainingPlan(userId, title, type, content, rationale));
    }

    @Transactional(readOnly = true)
    public List<TrainingPlan> listFor(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Tom hvis planen ikke finnes eller tilhører en annen bruker. */
    @Transactional
    public Optional<TrainingPlan> update(UUID id, UUID userId, String title, String type, JsonNode content) {
        return repository.findByIdAndUserId(id, userId).map(plan -> {
            plan.update(title, type, content);
            return repository.save(plan);
        });
    }

    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }
}
