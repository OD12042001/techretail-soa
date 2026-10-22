package com.techretail.techretail_monitoreo.salud;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MonitoreoProperties.class)
public class MonitoreoConfig {

    @Bean
    HealthProbe healthProbe(MonitoreoProperties propiedades) {
        return new HttpHealthProbe(propiedades.timeoutMs());
    }
}
