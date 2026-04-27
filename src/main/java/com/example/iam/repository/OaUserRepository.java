package com.example.iam.repository;

import com.example.iam.entity.OaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OaUserRepository extends JpaRepository<OaUser, Long> {
    Optional<OaUser> findByUsername(String username);
}
