package com.impulselock.impulselock.service;

import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate from {@link UserService} (which only ever operates on the authenticated caller's own
 * account) since these methods act on an arbitrary user by id - kept behind
 * {@code /api/v2/admin/**}, which {@code SecurityConfig} already restricts to {@code ROLE_ADMIN}
 * at the URL level.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
    }

    @Transactional
    public User updateStatus(Long id, boolean enabled) {
        User user = findById(id);
        user.setEnabled(enabled);
        return userRepository.save(user);
    }
}
