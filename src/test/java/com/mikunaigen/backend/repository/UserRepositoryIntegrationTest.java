package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role rolEstudiante;

    @BeforeEach
    void setUp() {
        rolEstudiante = new Role();
        rolEstudiante.setNombre("estudiante");
        rolEstudiante = roleRepository.save(rolEstudiante);
    }

    @Test
    void registroValido_persisteUsuarioPendiente() {
        // HU-01: registro con datos válidos guarda usuario pendiente con rol estudiante
        User user = new User();
        user.setNombres("Ana");
        user.setApellidos("Lopez");
        user.setEmail("ana.lopez@gmail.com");
        user.setTelefono("912345678");
        user.setDni("12345678");
        user.setContrasena("$2a$10$encodedhashvalue000000000000000000000000000000000");
        user.setEstado("pendiente");
        user.setRole(rolEstudiante);
        user.setAceptoTerminos(true);
        user.setAceptoDescargo(true);

        User guardado = userRepository.save(user);

        assertThat(guardado.getId()).isNotNull();
        assertThat(userRepository.findByEmail("ana.lopez@gmail.com")).isPresent();
        assertThat(guardado.getEstado()).isEqualTo("pendiente");
        assertThat(guardado.getRole().getNombre()).isEqualTo("estudiante");
    }

    @Test
    void emailDuplicado_existeEnBaseDeDatos() {
        // HU-01: email ya registrado impide nuevo registro
        User user = new User();
        user.setNombres("Pedro");
        user.setApellidos("Garcia");
        user.setEmail("duplicado@gmail.com");
        user.setTelefono("923456789");
        user.setContrasena("hash");
        user.setEstado("activo");
        user.setRole(rolEstudiante);
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("duplicado@gmail.com")).isTrue();
    }

    @Test
    void desactivarCuenta_estadoSuspendido() {
        // HU-07: desactivación de cuenta marca estado suspendido
        User user = new User();
        user.setNombres("Luis");
        user.setApellidos("Rios");
        user.setEmail("luis@gmail.com");
        user.setTelefono("934567890");
        user.setContrasena("hash");
        user.setEstado("activo");
        user.setRole(rolEstudiante);
        user = userRepository.save(user);

        user.setDeleted(true);
        userRepository.save(user);

        User actualizado = userRepository.findByEmail("luis@gmail.com").orElseThrow();
        assertThat(actualizado.isDeleted()).isTrue();
        assertThat(actualizado.getEstado()).isEqualToIgnoringCase("suspendido");
    }
}
