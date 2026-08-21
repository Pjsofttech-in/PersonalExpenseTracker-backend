package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.UserRequestDto;
import com.pjsofttech.expensetracker.dto.UserResponseDto;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.UserRepository;
import com.pjsofttech.expensetracker.service.UserService;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("pjsofttech/user")
//@CrossOrigin(origins = "http://localhost:5173")
public class UserController {
    @Autowired
    public UserService userService;
    @Autowired
    public UserRepository userRepository;

    @PostMapping
    public  ResponseEntity<?> addUser(@Valid @RequestBody UserRequestDto userRequestDto, Authentication authentication){
        String email = authentication.getName();
        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.addUser(userRequestDto,loggedInUser));

    }
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDto>> getUsers(
            Authentication authentication
    ) {

        String email = authentication.getName();

        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(
                userService.getContacts(loggedInUser)
        );
    }
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDto userRequestDto,
            Authentication authentication) {

        String email = authentication.getName();

        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(
                userService.updateUser(id, userRequestDto, loggedInUser)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                        Authentication authentication) {

        String email = authentication.getName();

        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(
                userService.deleteUser(id, loggedInUser));
    }
}
