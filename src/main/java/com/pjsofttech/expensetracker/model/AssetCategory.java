package com.pjsofttech.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "asset_categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_asset_category_owner_name",
                        columnNames = {"name", "owner_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}