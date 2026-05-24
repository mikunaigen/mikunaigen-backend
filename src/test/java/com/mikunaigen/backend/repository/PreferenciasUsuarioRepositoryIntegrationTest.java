package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.PreferenciasUsuario;
import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.PreferenciasUsuarioRepository;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/sql/h2-preferencias-usuario.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PreferenciasUsuarioRepositoryIntegrationTest {

    @Autowired
    private PreferenciasUsuarioRepository preferenciasRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private UUID usuarioEmprendedorId;

    @BeforeEach
    void setUp() {
        Role rol = new Role();
        rol.setNombre("emprendedor");
        rol = roleRepository.save(rol);

        User user = new User();
        user.setNombres("Carla");
        user.setApellidos("Mendoza");
        user.setEmail("carla@gmail.com");
        user.setTelefono("945678901");
        user.setContrasena("hash");
        user.setEstado("activo");
        user.setRole(rol);
        usuarioEmprendedorId = userRepository.save(user).getId();
    }

    @Test
    void guardarPreferenciasEmprendedor_persistePresupuesto() {
        // HU-06: guardado de preferencias con presupuesto para rol emprendedor
        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(usuarioEmprendedorId);
        pref.setEnfoquePrincipal("minimo_costo_produccion");
        pref.setPresupuestoMaximo(new BigDecimal("25.50"));
        pref.setPreferenciasCompletadas(true);
        pref.setCabezasOptimizacion(new String[] { "minimo_costo_produccion", "maxima_precision_nutricional" });

        preferenciasRepository.save(pref);

        PreferenciasUsuario recuperada = preferenciasRepository.findById(usuarioEmprendedorId).orElseThrow();
        assertThat(recuperada.getPresupuestoMaximo()).isEqualByComparingTo("25.50");
        assertThat(recuperada.getCabezasOptimizacion()).hasSize(2);
    }

    @Test
    void preferenciasEstudiante_sinPresupuesto() {
        // HU-06: rol estudiante no persiste presupuesto máximo
        Role rolEst = new Role();
        rolEst.setNombre("estudiante");
        rolEst = roleRepository.save(rolEst);

        User estudiante = new User();
        estudiante.setNombres("Diego");
        estudiante.setApellidos("Silva");
        estudiante.setEmail("diego@gmail.com");
        estudiante.setTelefono("956789012");
        estudiante.setContrasena("hash");
        estudiante.setEstado("activo");
        estudiante.setRole(rolEst);
        UUID id = userRepository.save(estudiante).getId();

        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(id);
        pref.setEnfoquePrincipal("maxima_precision_nutricional");
        pref.setPresupuestoMaximo(null);
        preferenciasRepository.save(pref);

        PreferenciasUsuario recuperada = preferenciasRepository.findById(id).orElseThrow();
        assertThat(recuperada.getPresupuestoMaximo()).isNull();
        assertThat(recuperada.getCabezasOptimizacion()[0]).isEqualTo("maxima_precision_nutricional");
    }
}
