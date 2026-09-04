package com.pjsofttech.expensetracker.model;

import lombok.Getter;

@Getter
public enum LiabilityType {

    // Bills
    WIFI_BILL(LiabilityGroup.BILL),
    MOBILE_BILL(LiabilityGroup.BILL),
    ELECTRIC_BILL(LiabilityGroup.BILL),
    TV_OTT_BILL(LiabilityGroup.BILL),
    INSURANCE(LiabilityGroup.BILL),

    // Fees
    SCHOOL_FEE(LiabilityGroup.FEE),
    TUITION_FEE(LiabilityGroup.FEE),

    // Loans
    BANK_LOAN(LiabilityGroup.LOAN),
    PERSONAL_LOAN(LiabilityGroup.LOAN),
    GOLD_LOAN(LiabilityGroup.LOAN),
    HOME_LOAN(LiabilityGroup.LOAN),
    VEHICLE_LOAN(LiabilityGroup.LOAN),
    EDUCATION_LOAN(LiabilityGroup.LOAN),
    BUSINESS_LOAN(LiabilityGroup.LOAN),
    MORTGAGE(LiabilityGroup.LOAN),

    // Credit
    CREDIT_CARD(LiabilityGroup.CREDIT_CARD),

    // Other
    BORROWED_MONEY(LiabilityGroup.OTHER),
    OTHER(LiabilityGroup.OTHER);

    private final LiabilityGroup group;

    LiabilityType(LiabilityGroup group) {
        this.group = group;
    }

    public LiabilityGroup getGroup() {
        return group;
    }
}
