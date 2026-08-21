package com.pjsofttech.expensetracker.controller;
import com.pjsofttech.expensetracker.dto.BankRequestDto;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.BankRepository;
import com.pjsofttech.expensetracker.repository.UserRepository;
import com.pjsofttech.expensetracker.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pjsofttech/bank")
public class BankController {
    @Autowired
    private BankService bankService;
    @Autowired
    private BankRepository bankRepository;
    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> addBank(@RequestBody BankRequestDto bankRequestDto, Authentication authentication){
        String email = authentication.getName();
        User loggedInUser = userRepository.findByEmail(email).orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bankService.addBank(bankRequestDto,loggedInUser));
    }
    @GetMapping
    public ResponseEntity<?> getAllBanks(Authentication authentication){
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.status(HttpStatus.OK)
                .body(bankService.getAllBanks(loggedInUser));
    }
    @DeleteMapping("{id}")
    public ResponseEntity<?> deleteBank(@PathVariable Long id,Authentication authentication){
        User loggedInUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.status(HttpStatus.OK)
                .body(bankService.deleteBank(id,loggedInUser));


    }
}
