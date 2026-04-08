package iuh.fit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRedisRepositories(basePackages = "iuh.fit.none")
@EnableAsync
@EnableScheduling
public class FruviaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FruviaBackendApplication.class, args);
	}

}
