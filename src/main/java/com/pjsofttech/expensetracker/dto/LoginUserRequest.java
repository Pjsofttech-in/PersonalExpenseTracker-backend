package com.pjsofttech.expensetracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data

public class LoginUserRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;
    @NotBlank(message = "Password is Required")
    private String password;
}
