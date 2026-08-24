package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Category;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {
    List<Category> findByOwner_Id(Long id);
    Optional<Category> findByIdAndOwner(Long id, User owner);

}
