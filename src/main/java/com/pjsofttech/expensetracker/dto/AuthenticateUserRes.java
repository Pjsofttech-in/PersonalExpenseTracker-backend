package com.pjsofttech.expensetracker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class AuthenticateUserRes {
    private String name;
    private String phoneNumber;
    private String email;
}
