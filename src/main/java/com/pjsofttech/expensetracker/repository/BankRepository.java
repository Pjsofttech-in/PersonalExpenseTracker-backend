package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Bank;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<Bank,Long> {
    List<Bank> findByOwner_Id(Long id);


    Optional<Bank> findByIdAndOwner(Long id,User owner);
}
