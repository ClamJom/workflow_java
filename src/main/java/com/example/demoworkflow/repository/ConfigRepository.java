package com.example.demoworkflow.repository;

import com.example.demoworkflow.pojo.Config;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigRepository extends JpaRepository<Config, Long> {
    Config getTopByName(String name);

    void deleteAllByName(String name);

    List<Config> getAllByParent(long parentId);
}
