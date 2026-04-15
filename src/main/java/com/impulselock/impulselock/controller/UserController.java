package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.model.UserProfile;
import com.impulselock.impulselock.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<UserProfile> upsertUser(@RequestBody UserProfile userProfile) {
        UserProfile saved = userRepository.upsertUser(userProfile);
        return ResponseEntity.ok(saved);
    }
}

