package no.weatheragent.nutrition;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FoodReportRepository extends JpaRepository<FoodReport, UUID> {

    boolean existsByFoodIdAndReporterId(UUID foodId, UUID reporterId);

    long countByReporterId(UUID reporterId);

    List<FoodReport> findByFoodIdIn(Collection<UUID> foodIds);

    long deleteByFoodId(UUID foodId);

    /** Én rad per rapportert vare: antall rapporter og siste tidspunkt. Flest rapporter først. */
    interface Summary {
        UUID getFoodId();

        Long getReports();

        Instant getLatest();
    }

    @Query("""
            select r.foodId as foodId, count(r) as reports, max(r.createdAt) as latest
            from FoodReport r
            group by r.foodId
            order by count(r) desc, max(r.createdAt) desc
            """)
    List<Summary> summarise(Pageable page);
}
