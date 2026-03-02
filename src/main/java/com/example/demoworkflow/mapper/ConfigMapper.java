package com.example.demoworkflow.mapper;

import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.vo.ConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ConfigMapper {

    ConfigMapper INSTANCE = Mappers.getMapper(ConfigMapper.class);

    Config configVOToConfig(ConfigVO configVO);

    ConfigVO configToConfigVO(Config config);

    List<ConfigVO> listConfigToListConfigVO(List<Config> list);

    List<Config> listConfigVOToListConfig(List<ConfigVO> list);
}
