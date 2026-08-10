package com.victorpena.shuttleflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.Organization;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);
}