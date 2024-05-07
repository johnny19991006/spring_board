package com.study.board.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.study.board.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    @Query("SELECT c from Company c where c.name = :name")
    Optional<Company> findByName(String name);
}
