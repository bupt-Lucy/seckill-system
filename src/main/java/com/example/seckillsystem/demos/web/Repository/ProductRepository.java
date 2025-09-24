package com.example.seckillsystem.demos.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.seckillsystem.demos.web.Product;
public interface ProductRepository extends JpaRepository<Product, Long> {
}