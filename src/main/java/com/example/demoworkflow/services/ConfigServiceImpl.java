package com.example.demoworkflow.services;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.mapper.ConfigMapper;
import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.repository.ConfigRepository;
import com.example.demoworkflow.vo.ConfigVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigServiceImpl implements ConfigService{
    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ConfigMapper configMapper;

    @Override
    public String getConfigValueByName(String name) {
        Config config = configRepository.getTopByName(name);
        if (config == null) return "";
        return config.getName();
    }

    @Override
    public ConfigVO getConfigByName(String name) {
        Config config = configRepository.getTopByName(name);
        if (config == null) return null;
        return configMapper.configToConfigVO(config);
    }

    @Override
    public List<ConfigVO> getSubConfigs(long parentId){
        List<Config> configs = configRepository.getAllByParent(parentId);
        return configMapper.listConfigToListConfigVO(configs);
    }

    @Override
    public List<ConfigVO> getAllConfig() {
        List<Config> configs = configRepository.findAll();
        return configMapper.listConfigToListConfigVO(configs);
    }

    @Override
    public void deleteAllConfig() {
        configRepository.deleteAll();
    }

    @Override
    public void deleteConfigByName(String name) {
        configRepository.deleteAllByName(name);
    }

    @Override
    public void updateConfig(ConfigVO configVO) {
        Config config = configMapper.configVOToConfig(configVO);
        configRepository.save(config);
    }

    @Override
    public void addConfig(ConfigVO configVO) {
        Config config = configMapper.configVOToConfig(configVO);
        configRepository.save(config);
    }

    @Override
    public void updateListConfig(List<ConfigVO> list){
        List<Config> configs = configMapper.listConfigVOToListConfig(list);
        configRepository.saveAll(configs);
    }
}
