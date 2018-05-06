package com.khuetla.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@EnableWebFluxSecurity
@SpringBootApplication
public class EdgeServiceApplication {

    // authentication
    @Bean
    MapReactiveUserDetailsService authentication() {
        return new MapReactiveUserDetailsService(
                User.withDefaultPasswordEncoder()
                        .username("user")
                        .password("pw")
                        .roles("USER")
                        .build());
    }

    // authorization
    @Bean
    SecurityWebFilterChain authorization(ServerHttpSecurity security) {
        return security
                .authorizeExchange().pathMatchers("/rl").authenticated()
                .anyExchange().permitAll()
                .and()
                .httpBasic()
                .and()
                .build();
    }

    @Bean
    DiscoveryClientRouteDefinitionLocator discoveryRoute(DiscoveryClient dc) {
        return new DiscoveryClientRouteDefinitionLocator(dc);
    }

    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, RequestRateLimiterGatewayFilterFactory rl) {
        return builder.routes()
                // basic proxy
                .route(
                        spec -> spec.path("/start")
                                .uri("http://start.spring.io:80/")
                )
                // load balanced proxy
                .route(
                        spec -> spec.path("/lb")
                                .uri("lb://customer-service/customers")
                )
                // custom filer 1
                .route(
                        spec -> spec.path("/cf1")
                                .filter((exchange, chain) ->
                                        chain.filter(exchange).then(Mono.fromRunnable(() -> {
                                            ServerHttpResponse response = exchange.getResponse();
                                            response.setStatusCode(HttpStatus.CONFLICT);
                                            response.getHeaders().setContentType(MediaType.APPLICATION_PDF);
                                        }))
                                )
                                .uri("lb://customer-service/customers")
                )
                // custom filer 2
                .route(
                        spec -> spec.path("/cf2/**")
                                .rewritePath("/cf2/(?<CID>.*)", "/customers/${CID}")
                                .uri("lb://customer-service")
                )
                // circuit breaker
                .route(
                        spec -> spec.path("/cb")
                                .hystrix("cb")
                                .uri("lb://customer-service/delay")
                )
                // rate limiter
                .route(
                        spec -> spec.path("/rl")
                                .filter(
                                        rl.apply(RedisRateLimiter.args(5, 10))
                                )
                                .uri("lb://customer-service/customers")
                )


                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(EdgeServiceApplication.class, args);
    }
}
