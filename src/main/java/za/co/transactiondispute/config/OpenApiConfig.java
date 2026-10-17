package za.co.transactiondispute.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionDisputeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transaction Dispute API")
                        .description("API for managing transaction disputes and dispute workflows")
                        .version("1.0.0"));
    }
}
