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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciasUsuarioServiceTest {

    @Mock
    private PreferenciasUsuarioRepository preferenciasRepo;

    @Mock
    private RestriccionIngredienteRepository restriccionRepo;

    @Mock
    private AlimentoDatasetRepository alimentoRepo;

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private PreferenciasUsuarioService service;

    @Test
    void estudianteContextBlocksPresupuesto() {
        // HU-06: estudiante context blocks presupuesto (puedePresupuesto false)
        UUID usuarioId = UUID.randomUUID();
        User user = userWithRole("estudiante");
        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(usuarioId);

        when(userRepo.findById(usuarioId)).thenReturn(Optional.of(user));
        when(preferenciasRepo.findById(usuarioId)).thenReturn(Optional.of(pref));

        Map<String, Object> contexto = service.obtenerContexto(usuarioId);

        @SuppressWarnings("unchecked")
        Map<String, Object> capacidades = (Map<String, Object>) contexto.get("capacidades");
        assertThat(capacidades.get("puedePresupuesto")).isEqualTo(false);

        Map<String, Object> guardado = service.guardarPreferencias(usuarioId, Map.of(
                "presupuestoMaximo", "25.50",
                "enfoquePrincipal", "maxima_precision_nutricional"
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> preferencias = (Map<String, Object>) guardado.get("preferencias");
        assertThat(preferencias.get("presupuestoMaximo")).isNull();
        verify(preferenciasRepo).save(pref);
    }

    @Test
    void emprendedorCanSavePresupuestoAndEnfoque() {
        // HU-06: emprendedor can save presupuesto and enfoque
        UUID usuarioId = UUID.randomUUID();
        User user = userWithRole("emprendedor");
        PreferenciasUsuario pref = new PreferenciasUsuario();
        pref.setUsuarioId(usuarioId);

        when(userRepo.findById(usuarioId)).thenReturn(Optional.of(user));
        when(preferenciasRepo.findById(usuarioId)).thenReturn(Optional.of(pref));
        when(preferenciasRepo.save(any(PreferenciasUsuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> guardado = service.guardarPreferencias(usuarioId, Map.of(
                "presupuestoMaximo", "18.75",
                "enfoquePrincipal", "minimo_costo_produccion"
        ));

        assertThat(pref.getPresupuestoMaximo()).isEqualByComparingTo(new BigDecimal("18.75"));
        assertThat(pref.getEnfoquePrincipal()).isEqualTo("minimo_costo_produccion");
        assertThat(pref.isPreferenciasCompletadas()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> preferencias = (Map<String, Object>) guardado.get("preferencias");
        assertThat((BigDecimal) preferencias.get("presupuestoMaximo")).isEqualByComparingTo(new BigDecimal("18.75"));
        assertThat(preferencias.get("enfoquePrincipal")).isEqualTo("minimo_costo_produccion");
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
