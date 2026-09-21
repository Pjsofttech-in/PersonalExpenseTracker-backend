package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.AssetRequestDto;
import com.pjsofttech.expensetracker.dto.AssetResponseDto;
import com.pjsofttech.expensetracker.model.*;
import com.pjsofttech.expensetracker.repository.*;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * FIXED from the previous version:
 *   - updateAcquisition() now also updates the linked purchase Expense row
 *     (amount, bank, paymentMethod, total, particular, date) so that the
 *     expense list never shows stale data and deleteAsset() always reverses
 *     the correct bank by the correct amount.
 *
 *     Previously updateAcquisition() reversed/reapplied the bank balance via
 *     BankBalanceService but left the Expense untouched. That caused two bugs:
 *       1. The expense list showed the old amount/bank after an acquisition edit.
 *       2. deleteAsset() reads asset.getPurchaseExpense() to know which bank to
 *          reverse and by how much — with a stale expense it would reverse the
 *          wrong bank or the wrong amount (double-reverse or under-reverse).
 */
@Service
public class AssetService {

    @Autowired
    private AssetCategoryRepository assetCategoryRepository;
    @Autowired private ExpenseService expenseService;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetAcquisitionRepository assetAcquisitionRepository;
    @Autowired private BankRepository bankRepository;
    @Autowired private LiabilityRepository liabilityRepository;
    @Autowired private BankBalanceService bankBalanceService;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    // ═════════════════════════════════════════════════════════════════════
    // CREATE ASSET + ACQUISITION + PURCHASE EXPENSE
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public AssetResponseDto addAsset(@Valid AssetRequestDto req, User loggedInUser) {

//        if (req.getBankId() != null && req.getLiabilityId() != null) {
//            throw new IllegalArgumentException(
//                    "Provide either bankId or liabilityId, not both.");
//        }
        validateFunding(req);

        Bank bank = null;
        Liability liability = null;

        if (req.getLiabilityId() != null) {
            liability = liabilityRepository
                    .findByIdAndOwner(req.getLiabilityId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException(
                            "Liability not found or does not belong to user."));

            if (liability.getStatus() != LiabilityStatus.ACTIVE) {
                throw new IllegalArgumentException(
                        "Cannot fund a purchase from a non-ACTIVE liability.");
            }

        } else if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException(
                        "Bank is required for BANK_TRANSFER.");
            }
            bank = bankRepository
                    .findByIdAndOwner(req.getBankId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException(
                            "Bank not found or does not belong to user."));
        }

        BigDecimal purchaseValue = req.getPurchaseValue().setScale(2, RoundingMode.HALF_UP);
        if(req.getType().getAssetGroup() != req.getAssetGroup()){
            throw new IllegalArgumentException("Asset Type does not belong to selected asset group");
        }

        // ── Create Asset ─────────────────────────────────────────────────────────
        Asset asset = Asset.builder()
                .owner(loggedInUser)
                .name(req.getName())
                .assetCategory(
                        assetCategoryRepository
                                .findByIdAndOwner(req.getAssetCategoryId(), loggedInUser)
                                .orElseThrow(() -> new RuntimeException(
                                        "Asset category not found or does not belong to user."))
                )
                .contact(
                        req.getContactId() != null
                                ? contactRepository
                                .findByIdAndOwner(req.getContactId(), loggedInUser)
                                .orElseThrow(() ->
                                             new RuntimeException(
                                                     "Contact not found or does not belong to user."))
                                : null
                )               .assetGroup(req.getAssetGroup())
                .type(req.getType())
                .description(req.getDescription())
                .purchaseValue(purchaseValue)
                .currentValue(req.getCurrentValue() != null
                        ? req.getCurrentValue().setScale(2, RoundingMode.HALF_UP)
                        : purchaseValue)
                .purchaseDate(req.getPurchaseDate())
                .build();

        Asset savedAsset = assetRepository.save(asset);

        // ── Create Acquisition ───────────────────────────────────────────────────
        AssetAcquisition acquisition = AssetAcquisition.builder()
                .asset(savedAsset)
                .amount(purchaseValue)
                .acquisitionDate(req.getPurchaseDate())
                .paymentMethod(req.getPaymentMethod())
                .bank(bank)
                .liability(liability)
                .settled(true)
                .remark(req.getRemark())
                .build();

        assetAcquisitionRepository.save(acquisition);
        savedAsset.setAcquisition(acquisition);

        // ── Create purchase Expense (handles bank debit internally) ──────────────
        expenseService.createAssetPurchaseExpense(savedAsset, acquisition, loggedInUser);

        return mapToResponse(savedAsset);
    }

    // ═════════════════════════════════════════════════════════════════════
    // READ
    // ═════════════════════════════════════════════════════════════════════

    public List<AssetResponseDto> getAllAssets(User loggedInUser) {
        return assetRepository.findByOwnerOrderByPurchaseDateDescIdDesc(loggedInUser)
                .stream().map(this::mapToResponse).toList();
    }

    public AssetResponseDto getAssetById(Long assetId, User loggedInUser) {
        return mapToResponse(findAsset(assetId, loggedInUser));
    }

    // ═════════════════════════════════════════════════════════════════════
    // UPDATE ASSET METADATA ONLY (name / type / description / purchaseDate
    //                             / purchaseValue label — no money movement)
    // ═════════════════════════════════════════════════════════════════════
    //name
    //assetGroup
    //type
    //description
    //assetCategory
    //contact
    //should update the above fields
    //but should not update the below fields
    //purchaseValue
    //purchaseDate
    //bank
    //liability
    //paymentMethod

    @Transactional
    public AssetResponseDto updateAsset(
            Long assetId,
            @Valid AssetRequestDto req,
            User loggedInUser) {

        Asset asset = findAsset(assetId, loggedInUser);
//        if (req.getType() == null || req.getAssetGroup() == null) {
//            throw new IllegalArgumentException(
//                    "Asset group and asset type are required."
//            );
//        }
        validateFunding(req);

        if (req.getType().getAssetGroup() != req.getAssetGroup()) {
            throw new IllegalArgumentException(
                    "Asset type does not belong to selected asset group."
            );
        }

        asset.setName(req.getName());
        asset.setAssetGroup(req.getAssetGroup());
        asset.setType(req.getType());
        asset.setDescription(req.getDescription());

        asset.setAssetCategory(assetCategoryRepository.findByIdAndOwner(req.getAssetCategoryId(),loggedInUser).orElseThrow(()->new RuntimeException("Asset not found")));

        Contact contact = null;

        if (req.getContactId() != null) {
            contact = contactRepository
                    .findByIdAndOwner(req.getContactId(), loggedInUser)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Contact not found or does not belong to user."));
        }

        asset.setContact(contact);

//        asset.setPurchaseDate(req.getPurchaseDate());

//        asset.setPurchaseValue(
//                req.getPurchaseValue().setScale(2, RoundingMode.HALF_UP)
//        );

        Asset updated = assetRepository.save(asset);

        return mapToResponse(updated);
    }


    // ═════════════════════════════════════════════════════════════════════
    // UPDATE ACQUISITION — reverses the old bank impact, reapplies the new
    // one, AND keeps the linked purchase Expense in sync.
    //
    // FIX: the previous version reversed/reapplied bank balance correctly
    // but never touched the Expense row.  deleteAsset() relies on
    // asset.getPurchaseExpense() to know which bank to reverse and by how
    // much — a stale Expense caused wrong reversals on delete.
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public AssetResponseDto updateAcquisition(Long assetId, @Valid AssetRequestDto req, User loggedInUser) {

        Asset asset = findAsset(assetId, loggedInUser);

        AssetAcquisition acquisition = assetAcquisitionRepository.findByAsset(asset)
                .orElseThrow(() -> new RuntimeException(
                        "Acquisition record not found for this asset."));
        if (req.getBankId() != null && req.getLiabilityId() != null) {
            throw new IllegalArgumentException(
                    "Provide either bankId or liabilityId, not both.");
        }

        // ── 1. Reverse the old bank debit (using acquisition's bank, not the
        //       expense's, because we haven't updated the expense yet) ─────────────
        if (acquisition.getBank() != null) {
            bankBalanceService.addAmount(acquisition.getBank(), acquisition.getAmount());
        }
        Long oldLiabilityId = acquisition.getLiability() != null
                ? acquisition.getLiability().getId()
                : null;

        if (!Objects.equals(oldLiabilityId, req.getLiabilityId())) {
            throw new IllegalArgumentException(
                    "Changing the liability funding requires deleting and recreating the asset.");
        }

        // Note: liability-funded acquisitions are not reversed — the loan still
        // exists regardless of which asset it funded. Changing the liability link
        // requires delete + recreate.

        // ── 2. Resolve new bank ──────────────────────────────────────────────────
        Bank newBank = null;
        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (req.getBankId() == null) {
                throw new IllegalArgumentException(
                        "Bank is required when payment method is BANK_TRANSFER.");
            }
            newBank = bankRepository.findByIdAndOwner(req.getBankId(), loggedInUser)
                    .orElseThrow(() -> new RuntimeException(
                            "Bank not found or does not belong to user."));
        }

        BigDecimal newAmount = req.getPurchaseValue().setScale(2, RoundingMode.HALF_UP);

        // ── 3. Update Acquisition record ─────────────────────────────────────────
        acquisition.setAmount(newAmount);
        acquisition.setAcquisitionDate(req.getPurchaseDate());
        acquisition.setPaymentMethod(req.getPaymentMethod());
        acquisition.setBank(newBank);
        assetAcquisitionRepository.save(acquisition);

        // ── 4. Update Asset metadata ─────────────────────────────────────────────
        asset.setPurchaseValue(newAmount);
//        asset.setPurchaseDate(req.getPurchaseDate());
        Asset updated = assetRepository.save(asset);

        // ── 5. Keep the linked purchase Expense in sync ──────────────────────────
        //
        //       This is the fix. deleteAsset() reads asset.getPurchaseExpense() to
        //       decide which bank to reverse and by how much. If we leave the Expense
        //       stale (old amount + old bank) after changing the acquisition, the
        //       delete will reverse the wrong bank / wrong amount.
        //
        //       We update every field that createAssetPurchaseExpense() originally set:
        //         - amount, total        (both equal purchaseValue for asset purchases;
        //                                 GST/TDS are always 0 per createAssetPurchaseExpense)
        //         - bank                 (must match the acquisition bank going forward)
        //         - paymentMethod        (informational, kept consistent)
        //         - particular           (reflects any name change on the asset)
        //         - date                 (reflects purchaseDate change)
        //
        //       paymentStatus, paymentType, sourceType, type, owner stay unchanged.

        Expense purchaseExpense = asset.getPurchaseExpense();
        if (purchaseExpense != null) {
            purchaseExpense.setAmount(newAmount);
            purchaseExpense.setTotal(newAmount);          // GST/TDS always 0 for asset purchases
            purchaseExpense.setBank(newBank);
            purchaseExpense.setPaymentMethod(req.getPaymentMethod());
            purchaseExpense.setParticular("Purchase - " + asset.getName());
            purchaseExpense.setDate(req.getPurchaseDate().atStartOfDay());
            expenseRepository.save(purchaseExpense);
        }

        // ── 6. Apply the new bank debit ──────────────────────────────────────────
        if (newBank != null) {
            bankBalanceService.deductAmount(newBank, newAmount);
        }

        return mapToResponse(updated);
    }

    // ═════════════════════════════════════════════════════════════════════
    // UPDATE ONLY CURRENT VALUE (never touches acquisition or expense)
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public AssetResponseDto updateCurrentValue(Long assetId, BigDecimal currentValue, User loggedInUser) {

        if (currentValue == null || currentValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current value cannot be negative.");
        }

        Asset asset = findAsset(assetId, loggedInUser);
        asset.setCurrentValue(currentValue.setScale(2, RoundingMode.HALF_UP));

        Asset updated = assetRepository.save(asset);
        return mapToResponse(updated);
    }

    // ═════════════════════════════════════════════════════════════════════
    // DELETE ASSET — reverses the bank impact via the purchase Expense,
    // deletes the Expense, then deletes the Asset (acquisition cascades).
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public String deleteAsset(Long assetId, User loggedInUser) {

        Asset asset = findAsset(assetId, loggedInUser);

        Expense purchaseExpense = asset.getPurchaseExpense();

        // Reverse actual cash movement exactly once, using the Expense as the
        // source of truth (it is now always kept in sync by updateAcquisition).
        if (purchaseExpense != null
                && purchaseExpense.getBank() != null
                && purchaseExpense.getPaymentStatus() == PaymentStatus.COMPLETE) {

            bankBalanceService.addAmount(
                    purchaseExpense.getBank(),
                    purchaseExpense.getTotal());
        }

        // Remove generated Expense
        if (purchaseExpense != null) {
            expenseRepository.delete(purchaseExpense);
        }

        // Remove Asset (AssetAcquisition cascades via orphanRemoval)
        assetRepository.delete(asset);

        return "Asset and related purchase expense deleted successfully.";
    }

    // ═════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════
    private void validateFunding(
            AssetRequestDto req) {

        if (req.getBankId() != null && req.getLiabilityId() != null) {
            throw new IllegalArgumentException(
                    "Provide either bankId or liabilityId, not both."
            );
        }

        if (req.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {

            if (req.getBankId() == null) {
                throw new IllegalArgumentException(
                        "Bank is required for BANK_TRANSFER."
                );
            }

            if (req.getLiabilityId() != null) {
                throw new IllegalArgumentException(
                        "BANK_TRANSFER cannot be funded by a liability."
                );
            }
        }

        if (req.getLiabilityId() != null
                && req.getBankId() != null) {

            throw new IllegalArgumentException(
                    "Liability-funded purchase cannot also specify a bank."
            );
        }
        if (req.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                && req.getBankId() != null) {
            throw new IllegalArgumentException(
                    "Bank can only be provided for BANK_TRANSFER.");
        }
    }


    private Asset findAsset(Long assetId, User loggedInUser) {
        return assetRepository.findByIdAndOwner(assetId, loggedInUser)
                .orElseThrow(() -> new RuntimeException("Asset not found"));
    }

    private AssetResponseDto mapToResponse(Asset asset) {

        AssetAcquisition acquisition = assetAcquisitionRepository.findByAsset(asset).orElse(null);

        return AssetResponseDto.builder()
                .id(asset.getId())
                .name(asset.getName())
//                .contactId(asset.getContact().getId())
                .contactId(
                        asset.getContact() != null
                                ? asset.getContact().getId()
                                : null
                )
                .assetCategoryId(asset.getAssetCategory().getId())
                .assetGroup(asset.getAssetGroup())
                .type(asset.getType())
                .description(asset.getDescription())
                .purchaseValue(asset.getPurchaseValue())
                .currentValue(asset.getCurrentValue())
                .purchaseDate(asset.getPurchaseDate())
                .paymentMethod(acquisition != null ? acquisition.getPaymentMethod() : null)
                .bankId(acquisition != null && acquisition.getBank() != null
                        ? acquisition.getBank().getId() : null)
                .bankName(acquisition != null && acquisition.getBank() != null
                        ? acquisition.getBank().getName() : null)
                .liabilityId(acquisition != null && acquisition.getLiability() != null
                        ? acquisition.getLiability().getId() : null)
                .liabilityName(acquisition != null && acquisition.getLiability() != null
                        ? acquisition.getLiability().getName() : null)
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}