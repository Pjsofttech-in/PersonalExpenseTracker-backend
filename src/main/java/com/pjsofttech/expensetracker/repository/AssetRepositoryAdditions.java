package com.pjsofttech.expensetracker.repository;

/**
 * ADD these two methods to the existing AssetRepository interface.
 *
 * They use COALESCE so that the query returns 0 (not null) when a user
 * has no assets, avoiding a NullPointerException in the service layer.
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 *   import com.pjsofttech.expensetracker.model.User;
 *   import org.springframework.data.jpa.repository.Query;
 *   import org.springframework.data.repository.query.Param;
 *   import java.math.BigDecimal;
 *
 *   // Sum of currentValue for all assets belonging to the user.
 *   @Query("SELECT COALESCE(SUM(a.currentValue), 0) FROM Asset a WHERE a.owner = :owner")
 *   BigDecimal getTotalCurrentValueByOwner(@Param("owner") User owner);
 *
 *   // Sum of purchaseValue — kept for reference / historical reporting.
 *   @Query("SELECT COALESCE(SUM(a.purchaseValue), 0) FROM Asset a WHERE a.owner = :owner")
 *   BigDecimal getTotalPurchaseValueByOwner(@Param("owner") User owner);
 *
 * ─────────────────────────────────────────────────────────────────────────
 *
 * This file is documentation only — it will not compile as-is.
 * Merge the annotations and method signatures into AssetRepository.java.
 */
public class AssetRepositoryAdditions { /* documentation only */ }
