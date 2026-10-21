package za.co.transactiondispute;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import za.co.transactiondispute.dto.AuthRequest;
import za.co.transactiondispute.dto.AuthResponse;
import za.co.transactiondispute.exception.ApiError;
import za.co.transactiondispute.exception.GlobalExceptionHandler;
import za.co.transactiondispute.exception.ResourceNotFoundException;
import za.co.transactiondispute.integration.EmailClient;
import za.co.transactiondispute.integration.SmsClient;
import za.co.transactiondispute.security.JwtAuthenticationFilter;
import za.co.transactiondispute.security.JwtService;
import za.co.transactiondispute.controller.AuthController;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityCoverageTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCoverJwtServiceAndFilterBranches() throws Exception {
        JwtService jwtService = new JwtService();
        setField(jwtService, "secretKey", "transaction-dispute-jwt-secret-change-me");
        setField(jwtService, "jwtExpirationMs", 3_600_000L);

        UserDetails user = User.withUsername("dev-user")
                .password("dev-pass123")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(user);
        assertEquals("dev-user", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, user));

        UserDetails other = User.withUsername("other-user")
                .password("pass")
                .authorities("USER")
                .build();
        assertFalse(jwtService.isTokenValid(token, other));
        assertFalse(jwtService.isTokenValid(null, user));
        assertFalse(jwtService.isTokenValid(token, null));
        assertFalse(jwtService.isTokenValid("bad-token", user));

        JwtService spyJwtService = spy(new JwtService());
        setField(spyJwtService, "secretKey", "transaction-dispute-jwt-secret-change-me");
        setField(spyJwtService, "jwtExpirationMs", 3_600_000L);
        doReturn(null).when(spyJwtService).extractUsername(token);
        assertFalse(spyJwtService.isTokenValid(token, user));

        java.lang.reflect.Method isTokenExpired = JwtService.class.getDeclaredMethod("isTokenExpired", String.class);
        isTokenExpired.setAccessible(true);
        setField(jwtService, "jwtExpirationMs", -1_000L);
        String expiredToken = jwtService.generateToken(user);
        assertTrue((Boolean) isTokenExpired.invoke(jwtService, expiredToken));
        assertTrue((Boolean) isTokenExpired.invoke(jwtService, "bad-token"));
        assertFalse(jwtService.isTokenValid(expiredToken, user));

        setField(jwtService, "jwtExpirationMs", 3_600_000L);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("dev-user")).thenReturn(user);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        MockHttpServletRequest requestWithoutToken = new MockHttpServletRequest();
        MockHttpServletResponse responseWithoutToken = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(requestWithoutToken, responseWithoutToken, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        String validToken = jwtService.generateToken(user);
        MockHttpServletRequest requestWithToken = new MockHttpServletRequest();
        requestWithToken.addHeader("Authorization", "Bearer " + validToken);
        MockHttpServletResponse responseWithToken = new MockHttpServletResponse();
        filter.doFilter(requestWithToken, responseWithToken, chain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.clearContext();
        MockHttpServletRequest badRequest = new MockHttpServletRequest();
        badRequest.addHeader("Authorization", "Bearer invalid-token");
        filter.doFilter(badRequest, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        when(userDetailsService.loadUserByUsername("dev-user")).thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("missing"));
        MockHttpServletRequest missingUserRequest = new MockHttpServletRequest();
        missingUserRequest.addHeader("Authorization", "Bearer " + validToken);
        filter.doFilter(missingUserRequest, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.clearContext();
        MockHttpServletRequest nullUsernameRequest = new MockHttpServletRequest();
        nullUsernameRequest.addHeader("Authorization", "Bearer " + validToken);
        JwtService nullUsernameJwtService = mock(JwtService.class);
        when(nullUsernameJwtService.extractUsername(validToken)).thenReturn(null);
        JwtAuthenticationFilter nullUsernameFilter = new JwtAuthenticationFilter(nullUsernameJwtService, userDetailsService);
        nullUsernameFilter.doFilter(nullUsernameRequest, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldCoverAlternateFilterAndBootstrapBranches() throws Exception {
        JwtService jwtService = new JwtService();
        setField(jwtService, "secretKey", "transaction-dispute-jwt-secret-change-me");
        setField(jwtService, "jwtExpirationMs", 3_600_000L);

        UserDetails user = User.withUsername("dev-user").password("dev-pass123").authorities("USER").build();
        String validToken = jwtService.generateToken(user);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("dev-user")).thenReturn(user);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        MockHttpServletRequest requestWithoutBearer = new MockHttpServletRequest();
        requestWithoutBearer.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        FilterChain nonBearerChain = mock(FilterChain.class);
        filter.doFilter(requestWithoutBearer, new MockHttpServletResponse(), nonBearerChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("existing", "auth"));
        MockHttpServletRequest requestWithExistingAuth = new MockHttpServletRequest();
        requestWithExistingAuth.addHeader("Authorization", "Bearer " + validToken);
        filter.doFilter(requestWithExistingAuth, new MockHttpServletResponse(), nonBearerChain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.clearContext();
        UserDetails otherUser = User.withUsername("other-user").password("pass").authorities("USER").build();
        when(userDetailsService.loadUserByUsername("dev-user")).thenReturn(otherUser);
        MockHttpServletRequest requestForDifferentUser = new MockHttpServletRequest();
        requestForDifferentUser.addHeader("Authorization", "Bearer " + validToken);
        filter.doFilter(requestForDifferentUser, new MockHttpServletResponse(), nonBearerChain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        EmailClient emailClient = new EmailClient();
        emailClient.sendEmail("customer@example.com", "subject", "body");

        SmsClient smsClient = new SmsClient();
        smsClient.sendSms("+27123456789", "body");

        try (MockedStatic<SpringApplication> mockedSpringApplication = mockStatic(SpringApplication.class)) {
            mockedSpringApplication.when(() -> SpringApplication.run(TransactionDisputeApplication.class, new String[]{}))
                    .thenReturn(null);
            TransactionDisputeApplication.main(new String[]{});
        }
    }

    @Test
    void shouldCoverAuthControllerAndExceptionHandling() throws Exception {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtService jwtService = mock(JwtService.class);
        AuthController controller = new AuthController(authenticationManager, jwtService);

        UserDetails user = User.withUsername("dev-user").password("dev-pass123").authorities("USER").build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, "dev-pass123", user.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        ResponseEntity<AuthResponse> result = controller.login(new AuthRequest("dev-user", "dev-pass123"));
        assertEquals("jwt-token", result.getBody().token());
        assertEquals("dev-user", result.getBody().username());

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

        ResponseEntity<ApiError> notFound = handler.handleResourceNotFound(new ResourceNotFoundException("Missing"), request);
        assertEquals(404, notFound.getStatusCode().value());
        assertEquals("Missing", notFound.getBody().getMessage());

        ResponseEntity<ApiError> badRequest = handler.handleBadRequest(new IllegalArgumentException("Bad"), request);
        assertEquals(400, badRequest.getStatusCode().value());
        assertEquals("Bad", badRequest.getBody().getMessage());

        ResponseEntity<ApiError> internalError = handler.handleGeneralException(new RuntimeException("Oops"), request);
        assertEquals(500, internalError.getStatusCode().value());
        assertEquals("Unexpected server error", internalError.getBody().getMessage());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
