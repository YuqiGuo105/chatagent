package com.example.chatagent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for pagination metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private int page;
    private int pageSize;
    private long total;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public static PaginationDto of(int page, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return PaginationDto.builder()
                .page(page)
                .pageSize(pageSize)
                .total(total)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }
}
