package com.pjsofttech.expensetracker.custom_exceptions;

public class CategoryNotFoundException extends Exception{
    public CategoryNotFoundException(String msg){
        super(msg);
    }
}
