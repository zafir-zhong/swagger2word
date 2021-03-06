package com.buzhishi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

/**
 * @author cuixiuyin
 * @description
 * @date: 2018/12/19 21:32
 */
@SpringBootApplication
@EnableSwagger2WebMvc
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
