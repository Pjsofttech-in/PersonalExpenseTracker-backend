package com.pjsofttech.expensetracker.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticateUserReq {
    @NotBlank(message = "Name is required")
    private String name;
    @NotBlank(message = "Phone Number is required")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Invalid phone number"
    )
    private String phoneNumber;
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;
    @NotBlank(message = "Password is required")
    private String password;
}
