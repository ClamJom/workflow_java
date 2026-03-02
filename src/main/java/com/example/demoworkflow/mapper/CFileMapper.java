package com.example.demoworkflow.mapper;

import com.example.demoworkflow.pojo.CFile;
import com.example.demoworkflow.vo.CFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CFileMapper {
    CFile cFileVoToCFile(CFileVO vo);
    CFileVO cFileToCFileVo(CFile cFile);

    List<CFileVO> cFileListToCFileVoList(List<CFile> cFile);
}
