package com.techretail.techretail_autenticacion.auth;

import com.techretail.techretail_autenticacion.security.Rol;
import com.techretail.techretail_autenticacion.usuario.RolEntidad;
import com.techretail.techretail_autenticacion.usuario.RolRepository;
import com.techretail.techretail_autenticacion.usuario.Usuario;
import com.techretail.techretail_autenticacion.usuario.UsuarioRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprueba tres usuarios de demostracion, uno por rol del catalogo.
 *
 * <p>El registro publico solo puede crear {@code CLIENTE} (ver
 * {@link AuthService}); sin estos usuarios no habria forma de demostrar los
 * roles {@code ADMIN} y {@code EMPLEADO}. El aprovisionamiento es idempotente:
 * si el usuario ya existe no se toca. Se apaga con
 * {@code techretail.auth.demo-users.enabled=false} (o {@code AUTH_DEMO_USERS=false})
 * y esta apagado en el perfil de pruebas.</p>
 *
 * <p>Las contrasenas de demo nunca se escriben en el log.</p>
 */
@Component
@ConditionalOnProperty(name = "techretail.auth.demo-users.enabled", havingValue = "true", matchIfMissing = true)
public class DemoUsuarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUsuarioSeeder.class);

    private record Demo(String usuario, String password, String nombre, Rol rol) {
    }

    private static final List<Demo> DEMO = List.of(
            new Demo("admin@techretail.pe", "Admin123!", "Administrador TechRetail", Rol.ADMIN),
            new Demo("empleado@techretail.pe", "Empleado123!", "Empleado TechRetail", Rol.EMPLEADO),
            new Demo("cliente@techretail.pe", "Cliente123!", "Cliente TechRetail", Rol.CLIENTE));

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;

    public DemoUsuarioSeeder(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Demo demo : DEMO) {
            if (usuarios.existsByUsuario(demo.usuario())) {
                continue;
            }
            RolEntidad rol = roles.findByNombre(demo.rol().name()).orElse(null);
            if (rol == null) {
                log.warn("No se aprovisiona {}: falta el rol {} en la tabla roles", demo.usuario(), demo.rol());
                continue;
            }
            Usuario nuevo = new Usuario();
            nuevo.setUsuario(demo.usuario());
            nuevo.setPassword(passwordEncoder.encode(demo.password()));
            nuevo.setNombre(demo.nombre());
            nuevo.setEstado("ACTIVO");
            nuevo.setRol(rol);
            usuarios.save(nuevo);
            log.info("Usuario de demostracion aprovisionado: {} ({})", demo.usuario(), demo.rol());
        }
    }
}
