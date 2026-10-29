package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.transactiondispute.dto.UserProfileDto;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.service.UserProfileService;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'LEAD', 'ADMIN')")
    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(Principal principal) {
        String username = resolveAuthenticatedUsername(principal);
        return ResponseEntity.ok(toDto(userProfileService.getProfile(username)));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'AGENT', 'LEAD', 'ADMIN')")
    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(@Valid @RequestBody UserProfileDto profile, Principal principal) {
        String username = resolveAuthenticatedUsername(principal);
        UserProfile existing = userProfileService.getProfile(username);

        UserProfile entity = UserProfile.builder()
                .username(username)
                .fullName(profile.getFullName())
                .role(existing.getRole())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .department(profile.getDepartment())
                .region(profile.getRegion())
                .build();
        return ResponseEntity.ok(toDto(userProfileService.saveProfile(username, entity)));
    }

    private String resolveAuthenticatedUsername(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("Authentication is required to access the customer profile.");
        }
        return principal.getName();
    }

    private UserProfileDto toDto(UserProfile profile) {
        return UserProfileDto.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .fullName(profile.getFullName())
                .role(profile.getRole())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .department(profile.getDepartment())
                .region(profile.getRegion())
                .build();
    }
}
