package com.ptit.rc_system.security;

import com.ptit.rc_system.entity.User;
import com.ptit.rc_system.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ensures the authenticated caller may only access/modify resources scoped to their own
 * userId, unless they hold ROLE_ADMIN. Throws AccessDeniedException (-> HTTP 403) otherwise.
 */
@Component
public class OwnershipGuard {

    private final UserRepository userRepository;

    public OwnershipGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void checkSelfOrAdmin(Long targetUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        Long currentUserId = userRepository.findByUserName(auth.getName())
                .map(User::getUserId)
                .orElse(null);

        if (targetUserId == null || !targetUserId.equals(currentUserId)) {
            throw new AccessDeniedException("Not allowed to access userId " + targetUserId);
        }
    }
}
