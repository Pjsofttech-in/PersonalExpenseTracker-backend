package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Asset;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}