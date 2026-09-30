package no.weatheragent.body;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Innveiinger: logg (upsert per dag), list (eldst først, for grafen) og slett. */
@Service
public class WeighInService {

    static final double MIN_KG = 20;
    static final double MAX_KG = 400;

    private final WeighInRepository repository;

    public WeighInService(WeighInRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WeighIn log(UUID userId, LocalDate date, double weightKg, LocalDate today) {
        if (Double.isNaN(weightKg) || weightKg < MIN_KG || weightKg > MAX_KG) {
            throw new IllegalArgumentException("Vekten må være mellom " + (int) MIN_KG + " og " + (int) MAX_KG + " kg.");
        }
        if (date.isBefore(today.minusYears(30))) {
            throw new IllegalArgumentException("Datoen er for langt tilbake i tid.");
        }
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Du kan ikke veie deg i fremtiden.");
        }
        double rounded = Math.round(weightKg * 10) / 10.0;
        WeighIn entry = repository.findByUserIdAndDate(userId, date)
                .map(existing -> { existing.setWeightKg(rounded); return existing; })
                .orElseGet(() -> new WeighIn(userId, date, rounded));
        return repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<WeighIn> listFor(UUID userId) {
        return repository.findByUserIdOrderByDateAsc(userId);
    }

    @Transactional
    public boolean delete(UUID id, UUID userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }
}
