package com.quan.ledgerservice.category;

import com.quan.ledgerservice.category.dto.CategoryResponse;
import com.quan.ledgerservice.category.dto.CreateCategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public List<CategoryResponse> getVisibleCategories(UUID userId) {
        List<Category> categories = categoryRepository.findVisible(userId);
        if (categories.isEmpty()) {
            initDefaultCategories();
            categories = categoryRepository.findVisible(userId);
        }
        return categories.stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, CreateCategoryRequest request) {
        String trimmedName = request.name().trim();
        CategoryFlow flow = request.flow();

        // Kiểm tra xem danh mục người dùng đã tạo hay chưa
        Optional<Category> existingUserCat = categoryRepository
                .findByUserIdAndNameIgnoreCaseAndFlowAndDeletedAtIsNull(userId, trimmedName, flow);
        if (existingUserCat.isPresent()) {
            return CategoryResponse.from(existingUserCat.get());
        }

        // Kiểm tra xem danh mục hệ thống đã có tên này hay chưa
        Optional<Category> existingSysCat = categoryRepository
                .findByUserIdIsNullAndNameIgnoreCaseAndFlowAndDeletedAtIsNull(trimmedName, flow);
        if (existingSysCat.isPresent()) {
            return CategoryResponse.from(existingSysCat.get());
        }

        Category category = new Category();
        category.setUserId(userId);
        category.setName(trimmedName);
        category.setFlow(flow);
        category.setIcon(request.icon() != null && !request.icon().isBlank() ? request.icon() : "Category");
        category.setColor(request.color() != null && !request.color().isBlank()
                ? request.color()
                : (flow == CategoryFlow.EXPENSE ? "#EF4444" : "#16A34A"));
        category.setSystem(false);
        category.setArchived(false);
        category.setDisplayOrder((short) 0);
        category.setUsageCount(0);

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void initDefaultCategories() {
        if (categoryRepository.countByDeletedAtIsNull() > 0) {
            return;
        }

        List<Category> defaults = new ArrayList<>();

        // 11 Danh mục Chi mặc định
        defaults.add(buildSystemCategory("Ăn uống", CategoryFlow.EXPENSE, "Storefront", "#EF4444", (short) 1));
        defaults.add(buildSystemCategory("Di chuyển", CategoryFlow.EXPENSE, "LocalShipping", "#F97316", (short) 2));
        defaults.add(buildSystemCategory("Mua sắm", CategoryFlow.EXPENSE, "ShoppingBag", "#EAB308", (short) 3));
        defaults.add(buildSystemCategory("Giải trí", CategoryFlow.EXPENSE, "LocalFireDepartment", "#16A34A", (short) 4));
        defaults.add(buildSystemCategory("Sức khoẻ", CategoryFlow.EXPENSE, "FavoriteBorder", "#F97316", (short) 5));
        defaults.add(buildSystemCategory("Hoá đơn & nhà ở", CategoryFlow.EXPENSE, "ReceiptLong", "#64748B", (short) 6));
        defaults.add(buildSystemCategory("Điện thoại & internet", CategoryFlow.EXPENSE, "PhoneIphone", "#0EA5E9", (short) 7));
        defaults.add(buildSystemCategory("Quà tặng", CategoryFlow.EXPENSE, "CardGiftcard", "#EC4899", (short) 8));
        defaults.add(buildSystemCategory("Thú cưng", CategoryFlow.EXPENSE, "Inventory2", "#A855F7", (short) 9));
        defaults.add(buildSystemCategory("Giáo dục", CategoryFlow.EXPENSE, "Category", "#2563EB", (short) 10));
        defaults.add(buildSystemCategory("Khác", CategoryFlow.EXPENSE, "Category", "#94A3B8", (short) 99));

        // 6 Danh mục Thu mặc định
        defaults.add(buildSystemCategory("Lương", CategoryFlow.INCOME, "Payments", "#16A34A", (short) 1));
        defaults.add(buildSystemCategory("Thưởng", CategoryFlow.INCOME, "CardGiftcard", "#16A34A", (short) 2));
        defaults.add(buildSystemCategory("Freelance", CategoryFlow.INCOME, "Storefront", "#16A34A", (short) 3));
        defaults.add(buildSystemCategory("Đầu tư", CategoryFlow.INCOME, "TrendingUp", "#16A34A", (short) 4));
        defaults.add(buildSystemCategory("Được tặng", CategoryFlow.INCOME, "Redeem", "#16A34A", (short) 5));
        defaults.add(buildSystemCategory("Khác", CategoryFlow.INCOME, "Category", "#94A3B8", (short) 99));

        categoryRepository.saveAll(defaults);
    }

    private Category buildSystemCategory(String name, CategoryFlow flow, String icon, String color, short order) {
        Category cat = new Category();
        cat.setUserId(null);
        cat.setName(name);
        cat.setFlow(flow);
        cat.setIcon(icon);
        cat.setColor(color);
        cat.setSystem(true);
        cat.setArchived(false);
        cat.setDisplayOrder(order);
        cat.setUsageCount(0);
        return cat;
    }
}
