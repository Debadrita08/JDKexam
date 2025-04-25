package com.debadritacodes.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AppStartupRunner implements CommandLineRunner {
    
    @Autowired
    private ApiService apiService;
    
    @Override
    public void run(String... args) throws Exception {
        apiService.executeOnStartup();
    }
}
