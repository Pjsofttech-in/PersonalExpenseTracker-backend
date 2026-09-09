package com.pjsofttech.expensetracker.dto;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timeStamp;

    public static<T> ApiResponse<T> success(String message, T data){
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timeStamp(LocalDateTime.now())
                .build();
    }

    public static<T> ApiResponse<T> error(String message){
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .timeStamp(LocalDateTime.now())
                .build();
    }
}
