package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.AssetCategory;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {

    List<AssetCategory> findByOwnerOrderByNameAsc(User owner);

    Optional<AssetCategory> findByIdAndOwner(Long id, User owner);

    boolean existsByNameIgnoreCaseAndOwner(String name, User owner);


}