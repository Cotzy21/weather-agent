package no.weatheragent.nutrition;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomFoodRepository extends JpaRepository<CustomFood, UUID> {

    List<CustomFood> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long deleteByIdAndOwnerId(UUID id, UUID ownerId);

    /** Egne + offentlig delte varer der navnet inneholder søket. Egne først. */
    @Query("""
            select f from CustomFood f
            where (f.ownerId = :userId or f.isPublic = true)
              and lower(f.name) like lower(concat('%', :query, '%'))
            order by case when f.ownerId = :userId then 0 else 1 end, lower(f.name)
            """)
    List<CustomFood> searchVisible(@Param("userId") UUID userId, @Param("query") String query, Pageable page);

    /** Varer med denne strekkoden som brukeren har lov å se. Egne først. */
    @Query("""
            select f from CustomFood f
            where f.barcode = :barcode and (f.ownerId = :userId or f.isPublic = true)
            order by case when f.ownerId = :userId then 0 else 1 end, f.createdAt
            """)
    List<CustomFood> findVisibleByBarcode(@Param("userId") UUID userId, @Param("barcode") String barcode);
}
