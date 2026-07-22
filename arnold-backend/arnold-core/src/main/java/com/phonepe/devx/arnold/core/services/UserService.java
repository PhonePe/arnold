package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.user.UserResponse;
import com.phonepe.olympus.im.models.user.UserDetails;

public interface UserService {
    UserResponse getOrCreateCurrentUser(UserDetails userDetails);

    void deleteUser(String userId);
}
