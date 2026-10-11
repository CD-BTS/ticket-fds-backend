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
		var json = jsonMapper.valueToTree(ApiResponse.failure(ResponseCode.RESOURCE_NOT_FOUND, null));

		assertEquals(4, json.size());
		assertFalse(json.get("success").asBoolean());
		assertEquals("RESOURCE_NOT_FOUND", json.get("code").asString());
		assertTrue(json.get("data").isNull());
	}

	@Test
	void distinguishesErrorsWithTheSameHttpStatus() {
		assertEquals(ResponseCode.RESOURCE_NOT_FOUND.getStatus(), ResponseCode.USER_NOT_FOUND.getStatus());
		assertEquals("RESOURCE_NOT_FOUND", ApiResponse.failure(ResponseCode.RESOURCE_NOT_FOUND, null).code());
		assertEquals("USER_NOT_FOUND", ApiResponse.failure(ResponseCode.USER_NOT_FOUND, null).code());
		assertEquals(ResponseCode.DATA_CONFLICT.getStatus(), ResponseCode.EVENT_ALREADY_EXISTS.getStatus());
		assertEquals("DATA_CONFLICT", ApiResponse.failure(ResponseCode.DATA_CONFLICT, null).code());
		assertEquals("EVENT_ALREADY_EXISTS", ApiResponse.failure(ResponseCode.EVENT_ALREADY_EXISTS, null).code());
		assertEquals(ResponseCode.EVENT_ALREADY_EXISTS.getMessage(),
				ApiResponse.failure(ResponseCode.EVENT_ALREADY_EXISTS, null).message());
	}

	@Test
	void rejectsSuccessCodeForErrors() {
		assertThrows(IllegalArgumentException.class,
				() -> ApiResponse.failure(ResponseCode.SUCCESS, null));
		assertThrows(IllegalArgumentException.class,
				() -> new ApiException(ResponseCode.SUCCESS));
	}
}
