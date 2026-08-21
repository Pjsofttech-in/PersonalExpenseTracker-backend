package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.CategoryRequestDto;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.UserRepository;
import com.pjsofttech.expensetracker.service.CategoryService;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("pjsofttech/category")
//@CrossOrigin(origins = "http://localhost:5173")

public class CategoryController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public ResponseEntity<?> addCategory(@Valid @RequestBody CategoryRequestDto categoryRequestDto,Authentication authentication){
        String email = authentication.getName();
        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.addCategory(categoryRequestDto,loggedInUser));
    }
    @GetMapping
    public ResponseEntity<?> getAllCategories(Authentication authentication){
        String email = authentication.getName();
        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.status(HttpStatus.OK)
                .body(categoryService.getAllCategories(loggedInUser));
    }
    @DeleteMapping("{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id, Authentication authentication){
        String email = authentication.getName();
        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User Not Found"));
        return ResponseEntity.status(HttpStatus.OK)
                .body(categoryService.deleteCategory(id,loggedInUser));
    }

}
