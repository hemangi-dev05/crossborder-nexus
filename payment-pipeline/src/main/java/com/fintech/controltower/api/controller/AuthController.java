
package com.fintech.controltower.api.controller;

import com.fintech.controltower.domain.entity.Customer;
import com.fintech.controltower.service.security.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegistrationRequest request
    ) {
        try {
            Customer customer = authService.register(
                    request.fullName(),
                    request.email(),
                    request.password(),
                    request.phoneNumber(),
                    request.countryCode(),
                    request.city()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(toResponse(customer));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new MessageResponse(e.getMessage())
            );

        } catch (IllegalStateException | DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new MessageResponse("Email is already registered")
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            if (request.email() == null || request.password() == null) {
                return ResponseEntity.badRequest().body(
                        new MessageResponse("Email and password are required")
                );
            }

            String email = request.email().trim().toLowerCase(Locale.ROOT);

            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    request.password()
                            )
                    );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(
                    context,
                    httpRequest,
                    httpResponse
            );

            return ResponseEntity.ok(
                    new MessageResponse("Login successful")
            );

        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Invalid email or password"));
        }
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(
                token.getHeaderName(),
                token.getToken()
        );
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getCountryCode(),
                customer.getCity(),
                customer.getRole().name(),
                customer.isEmailVerified()
        );
    }

    public record RegistrationRequest(
            String fullName,
            String email,
            String password,
            String phoneNumber,
            String countryCode,
            String city
    ) {}

    public record LoginRequest(
            String email,
            String password
    ) {}

    public record CustomerResponse(
            Long id,
            String fullName,
            String email,
            String phoneNumber,
            String countryCode,
            String city,
            String role,
            boolean emailVerified
    ) {}

    public record MessageResponse(String message) {}

    public record CsrfResponse(
            String headerName,
            String token
    ) {}
}
