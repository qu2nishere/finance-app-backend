package com.quan.budgetservice.budget;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SpendingSnapshotId implements Serializable {
    private UUID userId;
    private String periodKey;
    private UUID categoryId;
}
