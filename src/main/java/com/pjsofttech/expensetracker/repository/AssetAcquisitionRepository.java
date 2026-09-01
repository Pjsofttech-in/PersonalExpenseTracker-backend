package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Asset;
import com.pjsofttech.expensetracker.model.AssetAcquisition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetAcquisitionRepository extends JpaRepository<AssetAcquisition, Long> {

    Optional<AssetAcquisition> findByAsset(Asset asset);
}