package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.model.Bank;
import com.pjsofttech.expensetracker.repository.BankRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BankBalanceService {

    @Autowired
    private BankRepository bankRepository;


    public void deductAmount(
            Bank bank,
            BigDecimal amount) {

        if (bank == null) {
            return;
        }

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            return;
        }

        BigDecimal currentBalance =
                bank.getCurrentBalance();

        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }

        BigDecimal newBalance =
                currentBalance.subtract(amount);

        bank.setCurrentBalance(newBalance);

        bankRepository.save(bank);
    }


    public void addAmount(
            Bank bank,
            BigDecimal amount) {

        if (bank == null) {
            return;
        }

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            return;
        }

        BigDecimal currentBalance =
                bank.getCurrentBalance();

        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }

        BigDecimal newBalance =
                currentBalance.add(amount);

        bank.setCurrentBalance(newBalance);

        bankRepository.save(bank);
    }
}