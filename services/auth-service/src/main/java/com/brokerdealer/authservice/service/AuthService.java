package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;

    public Object login(Object request) {
        // TODO: implement — JIRA story BD-XX
        return null;
    }

    public void logout(String token) {
        // TODO: implement — JIRA story BD-XX
    }

    public Object getCurrentUser(String token) {
        // TODO: implement — JIRA story BD-XX
        return null;
    }
}
