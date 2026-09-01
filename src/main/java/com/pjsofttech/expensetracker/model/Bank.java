package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    @Column(
            name = "opening_balance",
            nullable = false,
            precision = 15,
            scale = 2
    )
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(
            name = "current_balance",
            nullable = false,
            precision = 15,
            scale = 2
    )
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;
}
