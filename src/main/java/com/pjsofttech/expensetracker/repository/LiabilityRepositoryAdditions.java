package com.pjsofttech.expensetracker.repository;

/**
 * ADD these two methods to the existing LiabilityRepository interface.
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 *   import com.pjsofttech.expensetracker.model.User;
 *   import org.springframework.data.jpa.repository.Query;
 *   import org.springframework.data.repository.query.Param;
 *   import java.math.BigDecimal;
 *
 *   // Sum of outstandingAmount across all ACTIVE liabilities for the user.
 *   @Query("""
 *          SELECT COALESCE(SUM(l.outstandingAmount), 0)
 *          FROM Liability l
 *          WHERE l.owner = :owner
 *            AND l.status = com.pjsofttech.expensetracker.model.LiabilityStatus.ACTIVE
 *          """)
 *   BigDecimal getTotalOutstandingByOwner(@Param("owner") User owner);
 *
 *   // All liabilities, including PAID_OFF/CANCELLED — for historical reference.
 *   @Query("SELECT COALESCE(SUM(l.originalAmount), 0) FROM Liability l WHERE l.owner = :owner")
 *   BigDecimal getTotalOriginalAmountByOwner(@Param("owner") User owner);
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 * This file is documentation only — it will not compile as-is.
 * Merge the annotations and method signatures into LiabilityRepository.java.
 */
public class LiabilityRepositoryAdditions { /* documentation only */ }
