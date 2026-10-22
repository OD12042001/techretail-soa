package com.techretail.techretail_inventario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TechretailInventarioApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
	}

	/**
	 * Prueba de regresion del bug de configuracion: mientras los archivos
	 * application.properties contenian YAML, Spring los ignoraba y estas
	 * propiedades no existian en el Environment.
	 */
	@Test
	void laConfiguracionDelModuloSeAplica() {
		assertThat(environment.getProperty("server.port")).isEqualTo("8085");
		assertThat(environment.getProperty("spring.application.name")).isEqualTo("techretail-inventario");
		assertThat(environment.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
	}

}