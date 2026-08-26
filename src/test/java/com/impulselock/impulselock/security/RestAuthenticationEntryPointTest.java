package com.impulselock.impulselock.security;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class RestAuthenticationEntryPointTest {

    @Mock
    private SecurityErrorResponseWriter writer;

    @Test
    void delegatesToTheSharedWriterWith401AndTheRequestUri() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(writer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v2/transactions/history");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("no token"));

        verify(writer).write(response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource", "/api/v2/transactions/history");
    }
}
