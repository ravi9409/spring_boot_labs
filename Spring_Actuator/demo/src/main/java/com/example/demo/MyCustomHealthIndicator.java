package com.example.demo;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

@Component
public class MyCustomHealthIndicator extends AbstractHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        builder.up()
                .withDetail("app", "App is Running Successfully")
                .withDetail("error", "Nothing! I'm good.");
        /*
builder.down()
.withDetail("app", "App is Not Running...")
.withDetail("error", "I am Not Ok ! .");
*/
    }
}