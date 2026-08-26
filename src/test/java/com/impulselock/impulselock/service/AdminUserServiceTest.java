package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.exception.UserNotFoundException;
import com.impulselock.impulselock.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private AdminUserService newService() {
        return new AdminUserService(userRepository);
    }

    @Test
    void findAllDelegatesToTheRepository() {
        AdminUserService service = newService();
        User user = new User();
        Page<User> page = new PageImpl<>(java.util.List.of(user));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        assertThat(service.findAll(Pageable.unpaged()).getContent()).containsExactly(user);
    }

    @Test
    void findByIdThrowsForAnUnknownId() {
        AdminUserService service = newService();
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateStatusTogglesEnabledAndSaves() {
        AdminUserService service = newService();
        User user = new User();
        user.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.updateStatus(1L, false);

        assertThat(updated.isEnabled()).isFalse();
    }
}
