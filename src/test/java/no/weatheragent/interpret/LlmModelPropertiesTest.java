package no.weatheragent.interpret;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sjekker at modellnavnene løses riktig fra application.properties + miljøvariabler, uten å starte Spring. */
class LlmModelPropertiesTest {

    private static String proModel(Map<String, Object> env) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("env", env));
        sources.addLast(new PropertiesPropertySource("app",
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))));
        // samme oppslag som @Value i OpenAiCompatibleChatClient
        return new PropertySourcesPropertyResolver(sources)
                .resolvePlaceholders("${llm.model.pro:${llm.model.smart:${llm.model}}}");
    }

    @Test
    void proFallsBackToSmartThenToTheSingleModel() throws IOException {
        assertEquals("local-model", proModel(Map.of()));
        assertEquals("stor", proModel(Map.of("llm.model.smart", "stor")));
    }

    @Test
    void proCanBeSetDirectlyOrThroughThePremiumAlias() throws IOException {
        assertEquals("aller-storst", proModel(Map.of("llm.model.pro", "aller-storst", "llm.model.smart", "stor")));
        assertEquals("premium-modell", proModel(Map.of("llm.model.premium", "premium-modell", "llm.model.smart", "stor")));
    }
}
