package za.co.transactiondispute.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.transactiondispute.entity.UserProfile;
import za.co.transactiondispute.repository.UserProfileRepository;
import za.co.transactiondispute.service.UserProfileService;

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
        existing.setRole(profile.getRole() == null || profile.getRole().isBlank() ? existing.getRole() : profile.getRole());
        existing.setEmail(profile.getEmail() == null || profile.getEmail().isBlank() ? existing.getEmail() : profile.getEmail());
        existing.setPhone(profile.getPhone() == null || profile.getPhone().isBlank() ? existing.getPhone() : profile.getPhone());
        existing.setDepartment(profile.getDepartment() == null || profile.getDepartment().isBlank() ? existing.getDepartment() : profile.getDepartment());
        existing.setRegion(profile.getRegion() == null || profile.getRegion().isBlank() ? existing.getRegion() : profile.getRegion());

        return userProfileRepository.save(existing);
    }

    private UserProfile createDefaultProfile(String username) {
        UserProfile defaultProfile = UserProfile.builder()
                .username(username)
                .fullName("Ndumiso Ngonyama")
                .role("Dispute Operations Lead")
                .email("ndumiso.ngonyama@bitpro.co.za")
                .phone("+27 11 555 0123")
                .department("Customer Operations")
                .region("Johannesburg, South Africa")
                .build();

        return userProfileRepository.save(defaultProfile);
    }
}
