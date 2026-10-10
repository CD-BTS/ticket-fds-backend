package com.bts.fairticket.common.auth;

import java.io.IOException;
import java.util.Collections;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

import com.bts.fairticket.common.exception.ApiException;
import com.bts.fairticket.common.response.ApiResponse;
import com.bts.fairticket.common.response.ResponseCode;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter extends OncePerRequestFilter {

	public static final String SERVICE_ATTRIBUTE = "com.bts.fairticket.authenticatedService";
	private final ApiKeyAuthenticator authenticator;
	private final JsonMapper jsonMapper;

	public ApiKeyFilter(ApiKeyAuthenticator authenticator, JsonMapper jsonMapper) {
		this.authenticator = authenticator;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = new UrlPathHelper().getPathWithinApplication(request);
		return !path.equals("/api/v1/users") && !path.startsWith("/api/v1/users/")
				&& !path.equals("/api/v1/events") && !path.startsWith("/api/v1/events/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String service;
		try {
			if (Collections.list(request.getHeaders("X-API-Key")).size() != 1) {
				throw new ApiException(ResponseCode.UNAUTHORIZED);
			}
			service = authenticator.authenticate(request.getHeader("X-API-Key"));
		} catch (ApiException exception) {
			ResponseCode code = exception.getResponseCode();
			response.setStatus(code.getStatus().value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			jsonMapper.writeValue(response.getWriter(), ApiResponse.failure(code, null));
			return;
		}
		request.setAttribute(SERVICE_ATTRIBUTE, service);
		filterChain.doFilter(request, response);
	}
}
