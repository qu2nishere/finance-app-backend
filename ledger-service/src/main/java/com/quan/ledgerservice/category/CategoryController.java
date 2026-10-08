package com.quan.ledgerservice.category;

import com.quan.ledgerservice.category.dto.CategoryResponse;
import com.quan.ledgerservice.category.dto.CreateCategoryRequest;
import com.quan.ledgerservice.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> getCategories(@CurrentUser UUID userId) {
        return categoryService.getVisibleCategories(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return categoryService.createCategory(userId, request);
    }
}
