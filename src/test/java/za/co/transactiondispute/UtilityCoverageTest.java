package za.co.transactiondispute;

import jakarta.persistence.Convert;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import za.co.transactiondispute.config.LocalDateTimeAttributeConverter;
import za.co.transactiondispute.config.OpenApiConfig;
import za.co.transactiondispute.entity.DisputeIdempotency;
import za.co.transactiondispute.entity.Transaction;
import za.co.transactiondispute.enums.TransactionType;
import za.co.transactiondispute.mapper.TransactionMapper;
import za.co.transactiondispute.middleware.RequestLoggingFilter;
import za.co.transactiondispute.utility.DateUtil;

import java.lang.reflect.Field;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UtilityCoverageTest {

    @Test
    void shouldCoverUtilityConfigAndMapperMethods() throws Exception {
        DateUtil.format(null);
        DateUtil.format(LocalDateTime.of(2024, 1, 2, 3, 4, 5));

        TransactionMapper transactionMapper = new TransactionMapper();
        Transaction transaction = Transaction.builder()
                .id(1L)
                .transactionReference("TX-7")
                .accountNumber("ACC-1001")
                .amount(new BigDecimal("99.00"))
                .currency("ZAR")
                .type(TransactionType.CREDIT)
                .merchantName("Alpha")
                .status("POSTED")
                .transactionDate(LocalDateTime.of(2024, 1, 2, 3, 4, 5))
                .description("Example")
                .build();

        za.co.transactiondispute.dto.TransactionDto dto = transactionMapper.toDto(transaction);
        assertEquals("TX-7", dto.getTransactionReference());
        assertNull(transactionMapper.toDto(null));
        assertNotNull(transactionMapper.toEntity(dto));
        assertNull(transactionMapper.toEntity(null));

        LocalDateTimeAttributeConverter converter = new LocalDateTimeAttributeConverter();
        LocalDateTime now = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
        String formatted = converter.convertToDatabaseColumn(now);
        assertEquals(now, converter.convertToEntityAttribute(formatted));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute("   "));
        long epochMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long epochSeconds = now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        assertEquals(now, converter.convertToEntityAttribute(String.valueOf(epochMillis)));
        assertEquals(now, converter.convertToEntityAttribute(String.valueOf(epochSeconds)));
        assertEquals(now, converter.convertToEntityAttribute("2024-01-02T03:04:05"));
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("bad-value"));

        Field createdAtField = DisputeIdempotency.class.getDeclaredField("createdAt");
        assertNotNull(createdAtField.getAnnotation(Convert.class));
        assertEquals(LocalDateTimeAttributeConverter.class, createdAtField.getAnnotation(Convert.class).converter());

        OpenApiConfig openApiConfig = new OpenApiConfig();
        assertEquals("Transaction Dispute API", openApiConfig.transactionDisputeOpenAPI().getInfo().getTitle());

        RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            response.setStatus(201);
            return null;
        }).when(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        requestLoggingFilter.doFilter(request, response, chain);
        assertEquals(201, response.getStatus());
    }
}
