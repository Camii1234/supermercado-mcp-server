package com.udea.supermercadomcp;

import com.udea.supermercadomcp.service.SupermercadoToolsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SupermercadoMcpApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupermercadoMcpApplication.class, args);
	}

	/** Publica los métodos @Tool de SupermercadoToolsService como herramientas del servidor MCP. */
	@Bean
	public ToolCallbackProvider supermercadoTools(SupermercadoToolsService tools) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(tools)
				.build();
	}

}
