package com.impulselock.impulselock.security;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RestAccessDeniedHandlerTest {

    @Mock
    private SecurityErrorResponseWriter writer;

    @Test
    void delegatesToTheSharedWriterWith403AndTheRequestUri() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(writer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v2/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(writer).write(response, HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource", "/api/v2/admin/users");
    }
}
