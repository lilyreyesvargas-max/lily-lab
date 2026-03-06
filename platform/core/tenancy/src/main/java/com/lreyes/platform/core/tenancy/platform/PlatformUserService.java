package com.lreyes.platform.core.tenancy.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio para gestión de platform admin users con BCrypt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUserService {

    private final PlatformUserJdbcRepository userRepo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<PlatformUser> findAll() {
        return userRepo.findAll();
    }

    public Optional<PlatformUser> findById(UUID id) {
        return userRepo.findById(id);
    }

    public Optional<PlatformUser> findByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    /**
     * Autentica un platform admin por username y password.
     *
     * @return el usuario si las credenciales son válidas
     */
    public Optional<PlatformUser> authenticate(String username, String rawPassword) {
        Optional<PlatformUser> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        PlatformUser user = userOpt.get();
        if (!user.isEnabled()) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * Crea un nuevo platform admin.
     */
    public PlatformUser create(String username, String rawPassword, String email, String fullName) {
        if (userRepo.existsByUsername(username)) {
            throw new IllegalArgumentException("Ya existe un usuario con username: " + username);
        }
        String hash = passwordEncoder.encode(rawPassword);
        PlatformUser user = new PlatformUser(username, hash, email, fullName);
        return userRepo.insert(user);
    }

    /**
     * Actualiza datos de un platform admin (sin cambiar password).
     */
    public void update(UUID id, String email, String fullName, boolean enabled) {
        PlatformUser user = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
        user.setEmail(email);
        user.setFullName(fullName);
        user.setEnabled(enabled);
        userRepo.update(user);
    }

    /**
     * Cambia la contraseña de un platform admin.
     */
    public void changePassword(UUID id, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        userRepo.updatePassword(id, hash);
    }

    /**
     * Elimina un platform admin.
     */
    public void delete(UUID id) {
        userRepo.delete(id);
    }

    /**
     * Seedea el platform admin por defecto si no existe.
     * <p>
     * Lee las credenciales desde variables de entorno:
     * <ul>
     *   <li>{@code PLATFORM_ADMIN_USER} (default: {@code platformadmin})</li>
     *   <li>{@code PLATFORM_ADMIN_PASSWORD} — si está ausente o vacío, el seed se omite.</li>
     * </ul>
     */
    public void seedDefaultAdmin() {
        String username = System.getenv().getOrDefault("PLATFORM_ADMIN_USER", "platformadmin");
        String password = System.getenv("PLATFORM_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            log.warn("PLATFORM_ADMIN_PASSWORD no definido — seed del admin omitido. " +
                    "Define la variable de entorno para crear el usuario inicial.");
            return;
        }
        if (!userRepo.existsByUsername(username)) {
            create(username, password, "admin@platform.local", "Platform Administrator");
            log.info("Platform admin creado: {}", username);
        }
    }
}
