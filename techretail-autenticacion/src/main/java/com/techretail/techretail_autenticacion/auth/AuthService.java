package com.techretail.techretail_autenticacion.auth;

import com.techretail.techretail_autenticacion.security.JwtProperties;
import com.techretail.techretail_autenticacion.security.JwtService;
import com.techretail.techretail_autenticacion.security.Rol;
import com.techretail.techretail_autenticacion.usuario.RolEntidad;
import com.techretail.techretail_autenticacion.usuario.RolRepository;
import com.techretail.techretail_autenticacion.usuario.Usuario;
import com.techretail.techretail_autenticacion.usuario.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios y emision de tokens del AutenticacionService.
 *
 * <p>Reglas que sostienen las pruebas de seguridad del profesor:</p>
 * <ul>
 *   <li>una alta publica siempre nace {@code CLIENTE}: el rol no se acepta
 *       desde el cuerpo, asi que no hay escalada de privilegios;</li>
 *   <li>la contrasena se guarda como hash BCrypt (cost 12), nunca en claro;</li>
 *   <li>un login fallido cuesta el mismo BCrypt que uno exitoso, para no
 *       delatar por tiempo si el usuario existe;</li>
 *   <li>todo rechazo de credenciales es el mismo {@code 401}, sin distinguir
 *       usuario inexistente, password incorrecta o cuenta inactiva.</li>
 * </ul>
 */
@Service
public class AuthService {

    /** BCrypt trunca silenciosamente mas alla de 72 bytes: aqui se rechaza. */
    private static final int BYTES_MAXIMOS_BCRYPT = 72;

    private static final String ROL_POR_DEFECTO = "CLIENTE";
    private static final String ESTADO_ACTIVO = "ACTIVO";

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    /**
     * Hash de una contrasena aleatoria que nadie conoce. Cuando el usuario no
     * existe se compara contra el, para que el tiempo de respuesta no lo delate.
     */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwordEncoder,
            JwtService jwtService, JwtProperties jwtProperties) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.hashSenuelo = passwordEncoder.encode("senuelo-de-tiempo-constante");
    }

    @Transactional
    public RegistroResponse registrar(RegistroRequest peticion) {
        String usuario = normalizar(peticion.usuario());
        validarPasswordQueBcryptPuedaProcesar(peticion.password());

        if (usuarios.existsByUsuario(usuario)) {
            throw new UsuarioDuplicadoException();
        }

        RolEntidad rol = roles.findByNombre(ROL_POR_DEFECTO).orElseThrow(() -> new IllegalStateException(
                "Falta el rol " + ROL_POR_DEFECTO + " en la tabla roles de db_autenticacion"));

        Usuario nuevo = new Usuario();
        nuevo.setUsuario(usuario);
        nuevo.setPassword(passwordEncoder.encode(peticion.password()));
        nuevo.setNombre(normalizarNombre(peticion.nombre()));
        nuevo.setEstado(ESTADO_ACTIVO);
        nuevo.setRol(rol);

        Usuario guardado = usuarios.save(nuevo);
        return new RegistroResponse(guardado.getId(), guardado.getUsuario(), guardado.getNombre(),
                rol.getNombre(), guardado.getEstado());
    }

    @Transactional(readOnly = true)
    public LoginResponse autenticar(LoginRequest peticion) {
        String usuario = normalizar(peticion.usuario());

        // Un password mas largo que el limite de BCrypt no puede ser el de nadie:
        // se rechaza como credencial invalida en vez de reventar con un 500.
        if (bytes(peticion.password()) > BYTES_MAXIMOS_BCRYPT) {
            throw new CredencialesInvalidasException();
        }

        Optional<Usuario> encontrado = usuarios.findByUsuario(usuario);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(peticion.password(), hashSenuelo);
            throw new CredencialesInvalidasException();
        }

        Usuario cuenta = encontrado.get();
        if (!passwordEncoder.matches(peticion.password(), cuenta.getPassword())) {
            throw new CredencialesInvalidasException();
        }
        if (!ESTADO_ACTIVO.equalsIgnoreCase(cuenta.getEstado())) {
            throw new CredencialesInvalidasException();
        }

        Rol rol = Rol.desde(cuenta.getRol() == null ? null : cuenta.getRol().getNombre())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario " + cuenta.getUsuario() + " tiene un rol fuera del catalogo"));

        String token = jwtService.emitir(cuenta.getUsuario(), rol, cuenta.getId());

        return new LoginResponse(token, "Bearer", jwtProperties.getExpirationMinutes() * 60,
                rol.name(), cuenta.getId(), cuenta.getNombre());
    }

    private static String normalizar(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizarNombre(String nombre) {
        return nombre == null || nombre.isBlank() ? null : nombre.trim();
    }

    private static int bytes(String valor) {
        return valor == null ? 0 : valor.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validarPasswordQueBcryptPuedaProcesar(String password) {
        if (bytes(password) > BYTES_MAXIMOS_BCRYPT) {
            throw new SolicitudInvalidaException(
                    "password no puede superar los " + BYTES_MAXIMOS_BCRYPT + " bytes");
        }
    }
}
