package com.pjsofttech.expensetracker.controller;

import com.pjsofttech.expensetracker.dto.AuthenticateUserReq;
import com.pjsofttech.expensetracker.dto.LoginUserRequest;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.service.UserService;
import com.pjsofttech.expensetracker.util.JWTUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pjsofttech_welcome")
//@CrossOrigin(origins = "http://localhost:5173")
public class WelcomeController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserService userService;
    @Autowired
    private JWTUtil jwtUtil;

    @GetMapping("/health")
    public ResponseEntity<?>health(){
        return ResponseEntity.status(HttpStatus.OK)
                .body("Healthy");
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody AuthenticateUserReq authUser){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.registerUser(authUser));
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginUserRequest loginUserRequest){
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginUserRequest.getEmail(),loginUserRequest.getPassword()));

        User user = userService.findByEmail(loginUserRequest.getEmail());
        String token =jwtUtil.generateToken(loginUserRequest.getEmail());
        return ResponseEntity.status(HttpStatus.OK)
                .body(token);
    }
}
