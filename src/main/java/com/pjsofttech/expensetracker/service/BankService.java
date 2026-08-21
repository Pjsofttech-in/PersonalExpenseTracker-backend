package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.BankRequestDto;
import com.pjsofttech.expensetracker.dto.BankResponseDto;
import com.pjsofttech.expensetracker.model.Bank;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.BankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BankService {
    @Autowired
    private BankRepository bankRepository;



    public BankResponseDto addBank(BankRequestDto bankRequestDto, User loggedInUser) {
        Bank bank = Bank.builder()
                .name(bankRequestDto.getName())
                .accountNumber(bankRequestDto.getAccountNumber())
                .branch(bankRequestDto.getBranch())
                .owner(loggedInUser)
                .ifsc(bankRequestDto.getIfsc())
                .accountType(bankRequestDto.getAccountType())
                .build();
        bankRepository.save(bank);

        return BankResponseDto.builder()
                .id(bank.getId())
                .branch(bank.getBranch())
                .accountNumber(bank.getAccountNumber())
                .name(bank.getName())
                .ifsc(bank.getIfsc())
                .accountType(bank.getAccountType())
                .build();


    }

    public List<BankResponseDto> getAllBanks(User loggedInUser) {
        List<Bank> banks = bankRepository.findByOwner_Id(loggedInUser.getId());
        return banks.stream()
                .map((b)->BankResponseDto.builder()
                        .id(b.getId()).name(b.getName()).accountNumber(b.getAccountNumber()).branch(b.getBranch())
                        .ifsc(b.getIfsc())
                        .accountType(b.getAccountType())
                                .build()).toList();
    }

    public String deleteBank(Long id, User loggedInUser) {
        Bank bank = bankRepository.findByIdAndOwner(id,loggedInUser).orElseThrow(()->new RuntimeException("Bank Not Found"));
        bankRepository.delete(bank);
        return "Bank Deleted !";

    }
}
