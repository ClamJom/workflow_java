package com.example.demoworkflow.repository;

import com.example.demoworkflow.pojo.CFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CFileRepository extends JpaRepository<CFile, Long> {
    CFile getCFilesByWorkspaceAndUuid(String workspace, String uuid);
    void deleteAllById(long id);
}
