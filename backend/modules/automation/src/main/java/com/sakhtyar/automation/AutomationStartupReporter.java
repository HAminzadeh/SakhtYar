package com.sakhtyar.automation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AutomationStartupReporter implements ApplicationRunner {
  private final AutomationPlatformService service;
  public AutomationStartupReporter(AutomationPlatformService service){this.service=service;}
  @Override public void run(ApplicationArguments args) {
    System.out.println("========================================================================");
    System.out.println(" SAKHTYAR AUTOMATION PLATFORM - STARTUP CHECK");
    System.out.println("========================================================================");
    try {
      var result=service.run(true);
      Object console=result.get("console");
      if(console!=null) System.out.println(console);
    } catch(Exception ex) {
      System.out.println("[WARN] Automation startup check: "+ex.getMessage());
    }
    System.out.println("========================================================================");
  }
}