package za.co.transactiondispute.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(Principal principal) {
        String username = principal != null ? principal.getName() : "dev-user";
        return ResponseEntity.ok(toDto(userProfileService.getProfile(username)));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(@Valid @RequestBody UserProfileDto profile, Principal principal) {
        String username = principal != null ? principal.getName() : "dev-user";
        UserProfile entity = UserProfile.builder()
                .username(username)
                .fullName(profile.getFullName())
                .role(profile.getRole())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .department(profile.getDepartment())
                .region(profile.getRegion())
                .build();
        return ResponseEntity.ok(toDto(userProfileService.saveProfile(username, entity)));
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
