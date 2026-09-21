package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.NetWorthSnapshot;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {

    /** All snapshots for a user, sorted chronologically (oldest first). */
    List<NetWorthSnapshot> findByOwnerOrderByYearAsc(User owner);

    /** Snapshot for a specific year, scoped to the owner. */
    Optional<NetWorthSnapshot> findByOwnerAndYear(User owner, Integer year);

    /**
     * Last N snapshots ordered descending — useful for calculating
     * average annual net-worth growth from recent history.
     */
    @Query("""
           SELECT s FROM NetWorthSnapshot s
           WHERE s.owner = :owner
           ORDER BY s.year DESC
           LIMIT :limit
           """)
    List<NetWorthSnapshot> findRecentByOwner(
            @Param("owner") User owner,
            @Param("limit") int limit);
}
