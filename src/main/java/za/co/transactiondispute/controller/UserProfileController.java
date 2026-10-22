package za.co.transactiondispute.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<UserProfile> getProfile(Principal principal) {
        String username = principal != null ? principal.getName() : "dev-user";
        return ResponseEntity.ok(userProfileService.getProfile(username));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfile> updateProfile(@RequestBody UserProfile profile, Principal principal) {
        String username = principal != null ? principal.getName() : "dev-user";
        return ResponseEntity.ok(userProfileService.saveProfile(username, profile));
    }
}
