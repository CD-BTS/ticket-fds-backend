package com.bts.fairticket.common.response;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.common.exception.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void serializesSuccessWithFourFields() {
		var json = jsonMapper.valueToTree(ApiResponse.success(Map.of("id", 1)));

		assertEquals(4, json.size());
		assertTrue(json.get("success").asBoolean());
		assertEquals("COMMON_200", json.get("code").asString());
		assertEquals(1, json.get("data").get("id").asInt());
		assertEquals("요청이 성공했습니다.", json.get("message").asString());
	}

	@Test
	void serializesErrorWithNullData() {
		var json = jsonMapper.valueToTree(ApiResponse.failure(ResponseCode.NOT_FOUND, null));

		assertEquals(4, json.size());
		assertFalse(json.get("success").asBoolean());
		assertEquals("COMMON_404", json.get("code").asString());
		assertTrue(json.get("data").isNull());
	}

	@Test
	void rejectsSuccessCodeForErrors() {
		assertThrows(IllegalArgumentException.class,
				() -> ApiResponse.failure(ResponseCode.SUCCESS, null));
		assertThrows(IllegalArgumentException.class,
				() -> new ApiException(ResponseCode.SUCCESS));
	}
}
