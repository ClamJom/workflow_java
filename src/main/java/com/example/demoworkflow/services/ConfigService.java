package com.example.demoworkflow.services;

import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.vo.ConfigVO;

import java.util.List;

public interface ConfigService {
    String getConfigValueByName(String name);

    ConfigVO getConfigByName(String name);

    List<ConfigVO> getSubConfigs(long parentId);

    List<ConfigVO> getAllConfig();

    void deleteAllConfig();

    void deleteConfigByName(String name);

    void addConfig(ConfigVO configVO);

    void updateConfig(ConfigVO configVO);

    void updateListConfig(List<ConfigVO> list);
}
