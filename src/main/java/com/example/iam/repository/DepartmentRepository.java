package com.example.iam.repository;

import com.example.iam.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByExternalId(String externalId);

    List<Department> findByParentExternalId(String parentExternalId);
}
