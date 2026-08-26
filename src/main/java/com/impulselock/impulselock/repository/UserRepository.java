package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Replaces V1's hand-written JdbcTemplate repository of the same name (see docs/v1/database.md).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
