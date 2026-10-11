package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.web.dto.SavePlanRequest;
import no.weatheragent.web.dto.TrainingPlanDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Atomic plan acceptance with a durable receipt, scoped to the authenticated owner. */
@Service
public class TrainingPlanBatchService {
    private final JdbcTemplate jdbc;
    private final TrainingPlanService plans;
    private final ObjectMapper mapper;

    public TrainingPlanBatchService(JdbcTemplate jdbc, TrainingPlanService plans, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.plans = plans;
        this.mapper = mapper;
    }

    @Transactional
    public List<TrainingPlanDto> save(UUID userId, UUID clientId, List<SavePlanRequest> workouts) {
        if (clientId == null || workouts == null || workouts.isEmpty() || workouts.size() > 7) {
            throw new IllegalArgumentException("En plan må inneholde mellom én og sju økter.");
        }
        // PostgreSQL transaction lock also serializes retries handled by different server instances.
        long lockId = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits();
        jdbc.queryForObject("select 1 from pg_advisory_xact_lock(?)", Integer.class, lockId);
        JsonNode request = mapper.valueToTree(workouts);
        List<Receipt> receipts = jdbc.query(
                "select request::text, response::text from training_plan_batches where user_id = ? and client_id = ?",
                (rs, row) -> new Receipt(rs.getString(1), rs.getString(2)), userId, clientId);
        if (!receipts.isEmpty()) {
            Receipt receipt = receipts.getFirst();
            try {
                if (!request.equals(mapper.readTree(receipt.request()))) {
                    throw new IllegalArgumentException("Dette lagringsforsøket tilhører en annen plan. Lag et nytt forslag.");
                }
                return mapper.readerForListOf(TrainingPlanDto.class).readValue(receipt.response());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Invalid plan receipt", e);
            }
        }
        List<TrainingPlanDto> saved = workouts.stream().map(w -> TrainingPlanDto.from(plans.save(
                userId, w.title(), w.type(), w.content() == null ? mapper.createObjectNode() : w.content(), w.rationale())))
                .toList();
        jdbc.update("insert into training_plan_batches(user_id, client_id, request, response) values (?, ?, cast(? as jsonb), cast(? as jsonb))",
                userId, clientId, request.toString(), mapper.valueToTree(saved).toString());
        return saved;
    }

    private record Receipt(String request, String response) {}
}
