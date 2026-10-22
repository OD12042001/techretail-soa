package com.techretail.techretail_gateway.config;

import com.techretail.techretail_gateway.security.JwtAuthenticationFilter;
import com.techretail.techretail_gateway.security.JwtProperties;
import com.techretail.techretail_gateway.security.JwtService;
import com.techretail.techretail_gateway.security.RestAccessDeniedHandler;
import com.techretail.techretail_gateway.security.RestAuthenticationEntryPoint;
import com.techretail.techretail_gateway.security.Rol;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Politica de seguridad del BUS (punto unico de entrada).
 *
 * <p>Reglas alineadas con Avance2 8.3. La autenticacion es stateless por
 * Bearer token: no hay sesion, ni formulario de login, ni HTTP Basic, y CSRF
 * esta deshabilitado porque no existe cookie de sesion que proteger.</p>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        String admin = Rol.ADMIN.name();
        String empleado = Rol.EMPLEADO.name();
        String cliente = Rol.CLIENTE.name();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(cabeceras -> cabeceras.frameOptions(marco -> marco.deny()))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        // Infraestructura publica (MonitoreoService consulta salud).
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Autenticacion: unico camino de negocio sin JWT.
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        // Descubrimiento UDDI (find). El publish queda restringido.
                        .requestMatchers(HttpMethod.GET, "/api/registro/servicios").permitAll()
                        // Catalogo: lectura publica, escritura solo ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/productos/**").permitAll()
                        .requestMatchers("/api/productos/**").hasRole(admin)
                        // Recursos con rol especifico.
                        .requestMatchers("/api/pagos/**").hasAnyRole(admin, cliente)
                        .requestMatchers("/api/inventario/**").hasAnyRole(admin, empleado)
                        .requestMatchers("/api/monitoreo/**", "/api/auditoria/**").hasAnyRole(admin, empleado)
                        .requestMatchers("/ws/facturacion", "/ws/facturacion/**").hasAnyRole(admin, empleado)
                        // Recursos que solo exigen estar autenticado.
                        .requestMatchers("/api/clientes/**", "/api/pedidos/**", "/api/notificaciones/**")
                        .authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .addFilterAfter(new JwtAuthenticationFilter(jwtService), SecurityContextHolderFilter.class);

        return http.build();
    }
}
