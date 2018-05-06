package com.khuetla.spring;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class CustomerServiceApplication {

    @Bean
    ApplicationRunner init(CustomerRepository cr) {
        return args -> {
            cr.deleteAll()
                    .thenMany(Flux.just("A", "B", "C", "D")
                            .map(s -> new Customer(null, s))
                            .flatMap(cr::save))
                    .thenMany(cr.findAll())
                    .subscribe(System.out::println);
        };
    }

    @Bean
    RouterFunction<?> routes(CustomerRepository cr) {
        return RouterFunctions
                .route(GET("/customers"),
                        request
                                -> ok().body(cr.findAll(), Customer.class))
                .andRoute(GET("/customers/{id}"),
                        request
                                -> ok().body(cr.findById(request.pathVariable("id")), Customer.class))
                .andRoute(GET("/delay"),
                        request
                                -> ok().body(Flux.just("Hello, World!").delayElements(Duration.ofSeconds(10)), String.class));
    }

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}


interface CustomerRepository extends ReactiveCrudRepository<Customer, String> {

}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class Customer {

    @Id
    private String id;
    private String name;
}
