package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class CustomerController {
    @GetMapping(value = "/mycustomers")
    public List<Customer> getAllCustomers() {
        System.out.println("CC-getAllCustomers() ");
        List<Customer> custList = new ArrayList<>();
        custList.add(new Customer(101, "Sri", "sri@jlc.com", 111, "Blore"));
        custList.add(new Customer(102, "Vas", "vas@jlc.com", 222, "Blore"));
        return custList;
    }
}