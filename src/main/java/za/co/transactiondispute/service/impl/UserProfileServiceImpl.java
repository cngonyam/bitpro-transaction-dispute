package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.repository.UserProfileRepository;
import za.co.transactiondispute.service.UserProfileService;

import java.util.Locale;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public UserProfile getProfile(String username) {
        return userProfileRepository.findByUsername(username)
                .orElseGet(() -> createDefaultProfile(username));
    }

    @Override
    @Transactional
    public UserProfile saveProfile(String username, UserProfile profile) {
        UserProfile existing = userProfileRepository.findByUsername(username)
                .orElseGet(() -> {
                    UserProfile defaultProfile = createDefaultProfile(username);
                    defaultProfile.setUsername(username);
                    return defaultProfile;
                });

        existing.setFullName(profile.getFullName() == null || profile.getFullName().isBlank() ? existing.getFullName() : profile.getFullName());
        existing.setRole(existing.getRole() == null || existing.getRole().isBlank() ? profile.getRole() : existing.getRole());
        existing.setEmail(profile.getEmail() == null || profile.getEmail().isBlank() ? existing.getEmail() : profile.getEmail());
        existing.setPhone(profile.getPhone() == null || profile.getPhone().isBlank() ? existing.getPhone() : profile.getPhone());
        existing.setDepartment(profile.getDepartment() == null || profile.getDepartment().isBlank() ? existing.getDepartment() : profile.getDepartment());
        existing.setRegion(profile.getRegion() == null || profile.getRegion().isBlank() ? existing.getRegion() : profile.getRegion());

        return userProfileRepository.save(existing);
    }

    private UserProfile createDefaultProfile(String username) {
        String normalizedUsername = username == null ? "" : username.trim();
        String mappedRole = defaultRoleForUsername(normalizedUsername);
        String defaultFullName = "Dispute Operations " + mappedRole;
        String defaultEmail = normalizedUsername.isBlank() ? "ops@bitpro.co.za" : normalizedUsername.toLowerCase(Locale.ROOT) + "@bitpro.co.za";

        UserProfile defaultProfile = UserProfile.builder()
                .username(normalizedUsername)
                .fullName(defaultFullName)
                .role(mappedRole)
                .email(defaultEmail)
                .phone("+27 11 555 0123")
                .department("Customer Operations")
                .region("Johannesburg, South Africa")
                .build();

        return userProfileRepository.save(defaultProfile);
    }

    private String defaultRoleForUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        if (normalized.contains("admin")) {
            return "ADMIN";
        }
        if (normalized.contains("agent") || normalized.equals("agent-user")) {
            return "AGENT";
        }
        return "CUSTOMER";
    }
}
