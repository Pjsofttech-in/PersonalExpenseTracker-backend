package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="banks")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Bank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String branch;
    @Column(name = "account_number")
    private String accountNumber;
    @Column(name="ifsc_code")
    private String ifsc;
    @Column(name="account_type")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;
}
