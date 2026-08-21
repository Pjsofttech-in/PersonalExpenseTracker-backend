package com.pjsofttech.expensetracker.service;

import com.pjsofttech.expensetracker.dto.CategoryRequestDto;
import com.pjsofttech.expensetracker.dto.CategoryResponseDto;
import com.pjsofttech.expensetracker.model.Category;
import com.pjsofttech.expensetracker.model.User;
import com.pjsofttech.expensetracker.repository.CategoryRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CategoryService {
    @Autowired
    private CategoryRepository categoryRepository;

    public CategoryResponseDto
    addCategory(CategoryRequestDto categoryRequestDto,User loggedInUser) {
       Category category = Category.builder()
               .name(categoryRequestDto.getName())
               .owner(loggedInUser)
               .build();
       categoryRepository.save(category);
       return CategoryResponseDto.builder()
               .name(category.getName())
               .build();
    }

    public List<CategoryResponseDto> getAllCategories(User loggedInUser) {
        return categoryRepository.findByOwner_Id(loggedInUser.getId())
                .stream()
                .map(c->CategoryResponseDto.builder()
                        .id(c.getId())
                        .name(c.getName()).build())
                .toList();
    }

    public String deleteCategory(Long id, User loggedInUser) {
        Category category = categoryRepository.findByIdAndOwner(id,loggedInUser).orElseThrow(()->new RuntimeException("Category Not Found"));
        categoryRepository.delete(category);
        return "Category with id "+id+" deleted Successfully !";
    }
}
