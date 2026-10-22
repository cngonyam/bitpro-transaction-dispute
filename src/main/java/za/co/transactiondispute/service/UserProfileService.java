package za.co.transactiondispute.service;

import za.co.transactiondispute.entity.UserProfile;

public interface UserProfileService {
    UserProfile getProfile(String username);
    UserProfile saveProfile(String username, UserProfile profile);
}
