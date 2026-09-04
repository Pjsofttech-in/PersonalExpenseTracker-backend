package com.pjsofttech.expensetracker.model;

import lombok.Getter;

@Getter
public enum AssetType {

    // ─────────────────────────────────────────────
    // INVESTMENTS
    // ─────────────────────────────────────────────

    BANK_ACCOUNT(AssetGroup.INVESTMENT),
    FIXED_DEPOSIT(AssetGroup.INVESTMENT),      // Bank FD
    RECURRING_DEPOSIT(AssetGroup.INVESTMENT),  // Bank RD
    BOND(AssetGroup.INVESTMENT),
    NPS(AssetGroup.INVESTMENT),
    ESOP(AssetGroup.INVESTMENT),
    PPF(AssetGroup.INVESTMENT),
    SIF(AssetGroup.INVESTMENT),

    // ─────────────────────────────────────────────
    // PRECIOUS METALS / JEWELLERY
    // ─────────────────────────────────────────────

    GOLD(AssetGroup.JEWELLERY),
    SILVER(AssetGroup.JEWELLERY),
    PLATINUM(AssetGroup.JEWELLERY),
    DIAMOND(AssetGroup.JEWELLERY),

    // ─────────────────────────────────────────────
    // MARKET INVESTMENTS
    // ─────────────────────────────────────────────

    INDIAN_STOCK(AssetGroup.MARKET_INVESTMENT),
    US_STOCK(AssetGroup.MARKET_INVESTMENT),
    MUTUAL_FUND(AssetGroup.MARKET_INVESTMENT),
    BITCOIN(AssetGroup.MARKET_INVESTMENT),

    // ─────────────────────────────────────────────
    // PROPERTY
    // ─────────────────────────────────────────────

    PLOT(AssetGroup.PROPERTY),
    FLAT(AssetGroup.PROPERTY),
    LAND(AssetGroup.PROPERTY),

    // ─────────────────────────────────────────────
    // PHYSICAL ASSETS
    // ─────────────────────────────────────────────

    VEHICLE(AssetGroup.PROPERTY),

    // ─────────────────────────────────────────────
    // OTHER ASSETS
    // ─────────────────────────────────────────────

    CASH(AssetGroup.CASH),
    ELECTRONICS(AssetGroup.OTHER),
    BUSINESS(AssetGroup.OTHER),
    OTHER(AssetGroup.OTHER);

    private final AssetGroup assetGroup;

    AssetType(AssetGroup assetGroup) {
        this.assetGroup = assetGroup;
    }
}
