package io.github.kxng0109.aiprcopilot.config;

import io.github.kxng0109.aiprcopilot.error.DiffTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestSizeLimitFilterTest {

	private static final long MAX_BYTES = 100L;

	@Mock
	private PrCopilotAnalysisProperties analysisProperties;

	@Mock
	private FilterChain filterChain;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private RequestSizeLimitFilter filter;

	@BeforeEach
	void setup() {
		lenient().when(analysisProperties.getMaxRequestBytes()).thenReturn(MAX_BYTES);
		filter = new RequestSizeLimitFilter(analysisProperties, objectMapper);
	}

	@Test
	void shouldReject413_whenContentLengthExceedsCap() throws Exception {
		MockHttpServletRequest request = apiRequest("x".repeat(101));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain, never()).doFilter(any(), any());
		assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
		assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getContentAsString()).contains("\"statusCode\":413");
		assertThat(response.getContentAsString()).contains("/api/v1/analyze-diff");
	}

	@Test
	void shouldReject413_whenChunkedBodyExceedsCap() throws Exception {
		HttpServletRequest request = chunkedRequest("y".repeat(150));
		MockHttpServletResponse response = new MockHttpServletResponse();
		doAnswer(invocation -> {
			HttpServletRequest wrapped = invocation.getArgument(0);
			wrapped.getInputStream().readAllBytes();
			return null;
		}).when(filterChain).doFilter(any(), any());

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(any(), any());
		assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
		assertThat(response.getContentAsString()).contains("\"statusCode\":413");
	}

	@Test
	void wrapper_shouldThrowDiffTooLarge_whenReadPastCap() throws Exception {
		HttpServletRequest request = chunkedRequest("w".repeat(120));
		MockHttpServletResponse response = new MockHttpServletResponse();
		ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(captor.capture(), any());
		HttpServletRequest wrapped = captor.getValue();
		assertThatThrownBy(() -> wrapped.getInputStream().readAllBytes())
				.isInstanceOf(DiffTooLargeException.class);
	}

	@Test
	void shouldPassThrough_whenBodyWithinCap() throws Exception {
		String payload = "{\"diff\":\"small\"}";
		MockHttpServletRequest request = apiRequest(payload);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
		verify(filterChain).doFilter(captor.capture(), any());
		byte[] downstream = captor.getValue().getInputStream().readAllBytes();
		assertThat(new String(downstream, StandardCharsets.UTF_8)).isEqualTo(payload);
		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	void shouldPassThrough_whenBodyExactlyAtCap() throws Exception {
		MockHttpServletRequest request = apiRequest("z".repeat(100));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(any(), any());
	}

	@Test
	void shouldSkip_whenMethodIsNotPost() throws Exception {
		MockHttpServletRequest request = apiRequest("x".repeat(500));
		request.setMethod("GET");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(any(), any());
	}

	@Test
	void shouldSkip_whenPathIsOutsideApi() throws Exception {
		MockHttpServletRequest request = apiRequest("x".repeat(500));
		request.setRequestURI("/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(any(), any());
	}

	private static MockHttpServletRequest apiRequest(String body) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("POST");
		request.setRequestURI("/api/v1/analyze-diff");
		request.setContentType(MediaType.APPLICATION_JSON_VALUE);
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}

	private static HttpServletRequest chunkedRequest(String body) throws Exception {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/v1/analyze-diff");
		when(request.getContextPath()).thenReturn("");
		when(request.getContentLengthLong()).thenReturn(-1L);
		lenient().when(request.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());
		lenient().when(request.getHeader(any())).thenReturn(null);
		ServletInputStream backing =
				new DelegatingServletInputStream(new ByteArrayInputStream(bytes));
		when(request.getInputStream()).thenReturn(backing);
		return request;
	}
}
