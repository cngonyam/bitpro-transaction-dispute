package za.co.transactiondispute;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import za.co.transactiondispute.exception.ApiError;
import za.co.transactiondispute.exception.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnBadRequestForMalformedJson() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/disputes");
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("Malformed JSON body");

        ResponseEntity<ApiError> response = handler.handleHttpMessageNotReadable(exception, request);

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Malformed JSON request payload", response.getBody().getMessage());
        assertEquals("/api/v1/disputes", response.getBody().getPath());
    }
}
