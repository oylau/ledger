package com.ledger.bdd;

import com.ledger.LedgerApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = LedgerApplication.class)
public class CucumberSpringConfiguration {
}
