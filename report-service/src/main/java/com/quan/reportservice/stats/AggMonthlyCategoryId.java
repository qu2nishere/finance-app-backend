package com.quan.reportservice.stats;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AggMonthlyCategoryId implements Serializable {
    private UUID userId;
    private String yearMonth;
    private UUID categoryId;
}
