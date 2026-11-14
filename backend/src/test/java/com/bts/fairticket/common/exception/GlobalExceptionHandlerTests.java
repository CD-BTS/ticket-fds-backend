package com.bts.fairticket.common.exception;

import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.ResponseCode;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTests.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.TestController.class})
class GlobalExceptionHandlerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsCommonSuccessResponse() throws Exception {
		mockMvc.perform(post("/test/requests").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"ticket\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("COMMON_200"))
				.andExpect(jsonPath("$.data.name").value("ticket"))
				.andExpect(jsonPath("$.message").value("요청이 성공했습니다."));
	}

	@Test
	void returnsValidationDetails() throws Exception {
		mockMvc.perform(post("/test/requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.data.errors[0].field").value("name"))
				.andExpect(jsonPath("$.data.errors[0].message").value("이름은 필수입니다."))
				.andExpect(jsonPath("$.data.errors[0].rejectedValue").doesNotExist());
	}

	@Test
	void handlesMalformedJson() throws Exception {
		mockMvc.perform(post("/test/requests").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."));
	}

	@Test
	void handlesWrongParameterType() throws Exception {
		mockMvc.perform(get("/test/number").param("value", "invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void handlesMissingParameter() throws Exception {
		mockMvc.perform(get("/test/number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void handlesMethodParameterValidation() throws Exception {
		mockMvc.perform(get("/test/number").param("value", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.data.errors[0].field").value("value"))
				.andExpect(jsonPath("$.data.errors[0].message").value("1 이상이어야 합니다."));
	}

	@Test
	void preservesMethodNotAllowedHeader() throws Exception {
		mockMvc.perform(get("/test/requests"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", containsString("POST")))
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void handlesUnsupportedContentType() throws Exception {
		mockMvc.perform(post("/test/requests").contentType(MediaType.TEXT_PLAIN).content("ticket"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void handlesUnknownPath() throws Exception {
		mockMvc.perform(get("/test/missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void handlesApiException() throws Exception {
		mockMvc.perform(get("/test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		mockMvc.perform(get("/test/error"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
				.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void treatsNonMvcConstraintViolationAsServerError() throws Exception {
		mockMvc.perform(get("/test/constraint-error"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
				.andExpect(jsonPath("$.data").isEmpty());
	}
	@Test
	void treatsInvalidReturnValueAsServerError() throws Exception {
		mockMvc.perform(get("/test/invalid-result"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.data").isEmpty());
	}

	// 테스트 전용 Controller: 운영 소스에는 비즈니스 API를 추가하지 않는다.
	@RestController
	@RequestMapping("/test")
	static class TestController {

		@PostMapping("/requests")
		public ApiResponse<RequestDto> create(@Valid @RequestBody RequestDto request) {
			return ApiResponse.success(request);
		}

		@GetMapping("/number")
		public ApiResponse<Map<String, Integer>> number(
				@RequestParam("value") @Min(value = 1, message = "1 이상이어야 합니다.") int value) {
			return ApiResponse.success(Map.of("value", value));
		}

		@GetMapping("/conflict")
		public void conflict() {
			throw new ApiException(ResponseCode.DATA_CONFLICT);
		}

		@GetMapping("/error")
		public void error() {
			throw new IllegalStateException("internal detail must not be exposed");
		}

		@GetMapping("/constraint-error")
		public void constraintError() {
			throw new ConstraintViolationException("internal validation detail", Set.of());
		}
		@GetMapping("/invalid-result")
		@NotNull
		public String invalidResult() {
			return null;
		}
	}

	record RequestDto(@NotNull(message = "이름은 필수입니다.") String name) {
	}
}
