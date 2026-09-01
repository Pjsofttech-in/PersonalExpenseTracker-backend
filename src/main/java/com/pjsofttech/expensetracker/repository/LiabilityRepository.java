package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Liability;
import com.pjsofttech.expensetracker.model.LiabilityStatus;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiabilityRepository extends JpaRepository<Liability, Long> {

    // Always scope reads to the owner — never plain findById() in service code.
    Optional<Liability> findByIdAndOwner(Long id, User owner);

    List<Liability> findByOwnerOrderByStartDateDescIdDesc(User owner);

    List<Liability> findByOwnerAndStatusOrderByStartDateDescIdDesc(User owner, LiabilityStatus status);
}