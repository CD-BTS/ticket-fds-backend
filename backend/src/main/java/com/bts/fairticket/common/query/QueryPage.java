package com.bts.fairticket.common.query;

import java.util.Map;
import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ResponseCode;

public record QueryPage(int page, int size, String orderBy) {
	public static QueryPage parse(Integer page, Integer size, String sort, Map<String, String> fields,
			String defaultField, String idField) {
		int number = page == null ? 0 : page;
		int limit = size == null ? 20 : size;
		if (number < 0 || limit < 1 || limit > 100 || (long) number * limit > Integer.MAX_VALUE) invalid();
		String[] parts = (sort == null ? defaultField + ",desc" : sort).split(",", -1);
		if (parts.length != 2 || !fields.containsKey(parts[0])
				|| !(parts[1].equals("asc") || parts[1].equals("desc"))) invalid();
		String field = fields.get(parts[0]);
		return new QueryPage(number, limit, field + " " + parts[1]
				+ (field.equals(idField) ? "" : ", " + idField + " " + parts[1]));
	}

	public int offset() { return page * size; }
	public static void invalid() { throw new ApiException(ResponseCode.INVALID_REQUEST); }
}
