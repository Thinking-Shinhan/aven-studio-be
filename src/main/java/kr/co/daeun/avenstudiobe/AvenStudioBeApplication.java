package kr.co.daeun.avenstudiobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AvenStudioBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvenStudioBeApplication.class, args);
    }

}
