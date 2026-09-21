package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Asset;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AssetRepository
        extends JpaRepository<Asset, Long> {

    List<Asset> findByOwnerOrderByPurchaseDateDescIdDesc(
            User owner
    );

    Optional<Asset> findByIdAndOwner(
            Long id,
            User owner
    );

    // Sum of currentValue for all assets belonging to the user.
    @Query("SELECT COALESCE(SUM(a.currentValue), 0) FROM Asset a WHERE a.owner = :owner")
    BigDecimal getTotalCurrentValueByOwner(@Param("owner") User owner);

            // Sum of purchaseValue — kept for reference / historical reporting.
            @Query("SELECT COALESCE(SUM(a.purchaseValue), 0) FROM Asset a WHERE a.owner = :owner")
    BigDecimal getTotalPurchaseValueByOwner(@Param("owner") User owner);
}