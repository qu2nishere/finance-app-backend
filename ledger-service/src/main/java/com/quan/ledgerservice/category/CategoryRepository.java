package com.quan.ledgerservice.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("""
           select c from Category c
           where c.deletedAt is null
             and (c.userId is null or c.userId = :userId)
           order by c.flow, c.usageCount desc, c.displayOrder, c.name
           """)
    List<Category> findVisible(@Param("userId") UUID userId);

    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    long countByDeletedAtIsNull();

    Optional<Category> findByUserIdAndNameIgnoreCaseAndFlowAndDeletedAtIsNull(UUID userId, String name, CategoryFlow flow);

    Optional<Category> findByUserIdIsNullAndNameIgnoreCaseAndFlowAndDeletedAtIsNull(String name, CategoryFlow flow);
}
