package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.PreferenciasUsuario;
import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import com.mikunaigen.backend.repository.sql.PreferenciasUsuarioRepository;
import com.mikunaigen.backend.repository.sql.RestriccionIngredienteRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParametrizacionFormulacionServiceTest {

    @Mock
    private PreferenciasUsuarioRepository preferenciasRepo;

    @Mock
    private RestriccionIngredienteRepository restriccionRepo;

    @Mock
    private AlimentoDatasetRepository alimentoRepo;

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private ParametrizacionFormulacionService service;

    @Test
    void estudianteCannotEnableEstacionalidad() {
        // HU-09: estudiante cannot enable estacionalidad
        UUID usuarioId = UUID.randomUUID();
        User user = userWithRole("estudiante");
        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(usuarioId);

        when(userRepo.findById(usuarioId)).thenReturn(Optional.of(user));
        when(preferenciasRepo.findById(usuarioId)).thenReturn(Optional.of(pref));
        when(preferenciasRepo.save(any(PreferenciasUsuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardar(usuarioId, Map.of(
                "cabezasOptimizacion", List.of(ParametrizacionFormulacionService.ENFOQUE_PRECISION),
                "filtroEstacionalidadActivo", true
        ));

        assertThat(pref.isFiltroEstacionalidadActivo()).isFalse();
        verify(preferenciasRepo).save(pref);
    }

    @Test
    void emprendedorValidPresupuesto() {
        // HU-10: emprendedor valid presupuesto
        UUID usuarioId = UUID.randomUUID();
        User user = userWithRole("emprendedor");
        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(usuarioId);

        when(userRepo.findById(usuarioId)).thenReturn(Optional.of(user));
        when(preferenciasRepo.findById(usuarioId)).thenReturn(Optional.of(pref));
        when(preferenciasRepo.save(any(PreferenciasUsuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.guardar(usuarioId, Map.of(
                "cabezasOptimizacion", List.of(ParametrizacionFormulacionService.ENFOQUE_PRECISION),
                "presupuestoMaximo", "15.00"
        ));

        assertThat(pref.getPresupuestoMaximo()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(result.get("message")).isEqualTo("Parametrización guardada correctamente.");
    }

    @Test
    void estudianteBlockedFromExtraCabezasOptimizacion() {
        // HU-11: estudiante blocked from extra cabezas optimizacion
        UUID usuarioId = UUID.randomUUID();
        User user = userWithRole("estudiante");
        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(usuarioId);

        when(userRepo.findById(usuarioId)).thenReturn(Optional.of(user));
        when(preferenciasRepo.findById(usuarioId)).thenReturn(Optional.of(pref));

        assertThatThrownBy(() -> service.guardar(usuarioId, Map.of(
                "cabezasOptimizacion", List.of(
                        ParametrizacionFormulacionService.ENFOQUE_PRECISION,
                        ParametrizacionFormulacionService.ENFOQUE_COSTO
                )
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Debes seleccionar entre 1 y 1 modo(s) de optimización.");
    }

    private static User userWithRole(String rol) {
        Role role = new Role();
        role.setNombre(rol);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@test.com");
        user.setRole(role);
        return user;
    }
}
