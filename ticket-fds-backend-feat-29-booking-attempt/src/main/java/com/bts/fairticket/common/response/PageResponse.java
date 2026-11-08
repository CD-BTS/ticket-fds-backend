package com.bts.fairticket.common.response;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
		long totalPages, boolean hasNext) {
	public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
		return new PageResponse<>(List.copyOf(content), page, size, total,
				(total + size - 1) / size, ((long) page + 1) * size < total);
	}
}
