package com.eventflow.service;

import com.eventflow.dto.AuthResponse;
import com.eventflow.dto.LoginRequest;
import com.eventflow.dto.RegisterRequest;
import com.eventflow.entity.Organization;
import com.eventflow.entity.User;
import com.eventflow.repository.OrganizationRepository;
import com.eventflow.repository.UserRepository;
import com.eventflow.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    public AuthService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists");
        }

        String baseSlug = toSlug(request.organizationName());
        String slug = baseSlug;
        int counter = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }

        Organization org = new Organization(request.organizationName(), slug);
        org = organizationRepository.save(org);

        User user = new User(
                org,
                request.email().toLowerCase().trim(),
                passwordEncoder.encode(request.password()),
                "ADMIN"
        );
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), org.getId(), user.getEmail(), user.getRole());

        return new AuthResponse(
                token,
                user.getId(),
                org.getId(),
                org.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        Organization org = user.getOrganization();
        String token = jwtService.generateToken(user.getId(), org.getId(), user.getEmail(), user.getRole());

        return new AuthResponse(
                token,
                user.getId(),
                org.getId(),
                org.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "org-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("").toLowerCase(Locale.ENGLISH);
        return slug.isBlank() ? "org-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
