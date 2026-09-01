package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Liability;
import com.pjsofttech.expensetracker.model.LiabilityPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LiabilityPaymentRepository extends JpaRepository<LiabilityPayment, Long> {

    List<LiabilityPayment> findByLiabilityOrderByPaymentDateAsc(Liability liability);

    @Query("SELECT COALESCE(SUM(p.principalComponent), 0) FROM LiabilityPayment p WHERE p.liability = :liability")
    BigDecimal getTotalPrincipalPaid(@Param("liability") Liability liability);

    @Query("SELECT COALESCE(SUM(p.interestComponent), 0) FROM LiabilityPayment p WHERE p.liability = :liability")
    BigDecimal getTotalInterestPaid(@Param("liability") Liability liability);
}