package ru.max.raganalyzer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.max.raganalyzer.dto.AuthResponse;
import ru.max.raganalyzer.dto.LoginRequest;
import ru.max.raganalyzer.dto.RegisterRequest;
import ru.max.raganalyzer.entity.UserEntity;
import ru.max.raganalyzer.repository.UserRepository;
import ru.max.raganalyzer.security.SecurityUtils;
import ru.max.raganalyzer.security.UserPrincipal;
import ru.max.raganalyzer.service.JwtService;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        String email    = req.email()    != null ? req.email().trim().toLowerCase()    : "";
        String password = req.password() != null ? req.password() : "";

        if (!EMAIL_RE.matcher(email).matches()) {
            return bad("Некорректный email");
        }
        if (password.length() < 8) {
            return bad("Пароль должен быть не менее 8 символов");
        }
        if (password.length() > 128) {
            return bad("Пароль слишком длинный");
        }
        if (userRepository.existsByEmail(email)) {
            return bad("Пользователь с таким email уже существует");
        }

        UserEntity user = new UserEntity(email, passwordEncoder.encode(password));
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String email    = req.email()    != null ? req.email().trim().toLowerCase()    : "";
        String password = req.password() != null ? req.password() : "";

        UserEntity user = userRepository.findByEmail(email).orElse(null);

        // Одинаковое сообщение для несуществующего email и неверного пароля
        // — не даём enumeration-атаке узнать какие email зарегистрированы
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return bad("Неверный email или пароль");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getEmail()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        UserPrincipal p = SecurityUtils.currentUser();
        return ResponseEntity.ok(Map.of("userId", p.userId(), "email", p.email()));
    }

    private ResponseEntity<Map<String, String>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
