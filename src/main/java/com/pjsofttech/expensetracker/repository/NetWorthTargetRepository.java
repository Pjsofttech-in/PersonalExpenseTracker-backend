package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.NetWorthTarget;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetWorthTargetRepository extends JpaRepository<NetWorthTarget, Long> {

    /** Returns the single active target for a user, if one exists. */
    Optional<NetWorthTarget> findByOwnerAndActiveTrue(User owner);

    /** All targets ever created by a user (active and inactive), newest first. */
    List<NetWorthTarget> findByOwnerOrderByCreatedAtDesc(User owner);

    /** Deactivate all active targets for the given user in one query. */
    @Modifying
    @Query("UPDATE NetWorthTarget t SET t.active = false WHERE t.owner = :owner AND t.active = true")
    void deactivateAllForOwner(@Param("owner") User owner);

    /** Ownership-scoped lookup — used before deleting a specific target by ID. */
    Optional<NetWorthTarget> findByIdAndOwner(Long id, User owner);
}
