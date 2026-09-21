package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Liability;
import com.pjsofttech.expensetracker.model.LiabilityStatus;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
        import com.pjsofttech.expensetracker.model.User;
    import org.springframework.data.jpa.repository.Query;
    import org.springframework.data.repository.query.Param;
    import java.math.BigDecimal;

public interface LiabilityRepository extends JpaRepository<Liability, Long> {

    // Always scope reads to the owner — never plain findById() in service code.
    Optional<Liability> findByIdAndOwner(Long id, User owner);

    List<Liability> findByOwnerOrderByStartDateDescIdDesc(User owner);

    List<Liability> findByOwnerAndStatusOrderByStartDateDescIdDesc(User owner, LiabilityStatus status);


            // Sum of outstandingAmount across all ACTIVE liabilities for the user.
            @Query("""
           SELECT COALESCE(SUM(l.outstandingAmount), 0)
           FROM Liability l
           WHERE l.owner = :owner
             AND l.status = com.pjsofttech.expensetracker.model.LiabilityStatus.ACTIVE
           """)
    BigDecimal getTotalOutstandingByOwner(@Param("owner") User owner);

            // All liabilities, including PAID_OFF/CANCELLED — for historical reference.
            @Query("SELECT COALESCE(SUM(l.originalAmount), 0) FROM Liability l WHERE l.owner = :owner")
    BigDecimal getTotalOriginalAmountByOwner(@Param("owner") User owner);
}