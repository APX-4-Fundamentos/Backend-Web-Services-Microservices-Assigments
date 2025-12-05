package apx.inc.assignments_service.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI learninfPlatformOpenApi() {
// General configuration
        var openApi = new OpenAPI();
        openApi
                .info(new Info()
                        .title("APX LearnHive API")
                        .description("Backend API for APX LearnHive educational platform - Innovative learning solutions powered by APX Startup")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("APX Development Team")
                                .email("dev@apxlearnhive.com")
                                .url("https://apxlearnhive.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("APX LearnHive Platform Documentation")
                        .url("https://docs.apxlearnhive.com"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8083")
                                .description("Assignments (Microservice)")
                ));

        // Add security scheme
        final String securitySchemeName = "bearerAuth";

        openApi.addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));

        // Return the OpenAPI configuration object with all the settings
        return openApi;
    }
}
