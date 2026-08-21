package com.pjsofttech.expensetracker.repository;

import com.pjsofttech.expensetracker.model.Contact;
import com.pjsofttech.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact,Long> {
    List<Contact> findByOwner_Id(Long id);
    Contact findByEmail(String email);
    Optional<Contact> findByIdAndOwner(Long id, User owner);


}
