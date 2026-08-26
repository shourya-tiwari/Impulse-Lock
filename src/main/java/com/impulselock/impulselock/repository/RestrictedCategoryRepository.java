package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.RestrictedCategory;
import com.impulselock.impulselock.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestrictedCategoryRepository extends JpaRepository<RestrictedCategory, Long> {

    List<RestrictedCategory> findByUser(User user);

    Optional<RestrictedCategory> findByUserAndCategoryIgnoreCase(User user, String category);
}
