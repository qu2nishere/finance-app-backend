package com.quan.ledgerservice.category.dto;

import com.quan.ledgerservice.category.CategoryFlow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
        String name,

        @NotNull(message = "Loại dòng tiền (flow) không được để trống")
        CategoryFlow flow,

        String icon,

        String color
) {
}
