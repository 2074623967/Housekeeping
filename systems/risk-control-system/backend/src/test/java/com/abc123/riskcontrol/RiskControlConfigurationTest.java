package com.abc123.riskcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class RiskControlConfigurationTest {

    @Test
    void shouldUseLocalhostForOpsConfigDefault() throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertEquals(
                "http://localhost:18170/api/ops-config/effective-snapshot",
                resolver.getProperty("risk-control.ops-config.snapshot-url"));
    }
}
