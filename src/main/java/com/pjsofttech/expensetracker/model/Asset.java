package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─────────────────────────────────────────────
    // OWNER
    // ─────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_category_id", nullable = false)
    private AssetCategory assetCategory;
    // ─────────────────────────────────────────────
    // BASIC INFORMATION
    // ─────────────────────────────────────────────

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetGroup assetGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type;

    @Column(name = "description", length = 1000)
    private String description;

    // ─────────────────────────────────────────────
    // VALUES
    // ─────────────────────────────────────────────

    @Column(name = "purchase_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchaseValue;

    @Column(name = "current_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentValue;

    // ─────────────────────────────────────────────
    // PURCHASE DETAILS (denormalized for quick reads)
    // ─────────────────────────────────────────────

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;


    @OneToOne(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private Expense purchaseExpense;

    // ─────────────────────────────────────────────
    // ACQUISITION — replaces the old `bank` + `purchaseExpense` fields
    // ─────────────────────────────────────────────

    @OneToOne(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AssetAcquisition acquisition;

    // ─────────────────────────────────────────────
    // AUDIT
    // ─────────────────────────────────────────────

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.currentValue == null) {
            this.currentValue = this.purchaseValue;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}