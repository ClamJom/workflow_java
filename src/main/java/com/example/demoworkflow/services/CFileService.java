package com.example.demoworkflow.services;

import com.example.demoworkflow.pojo.CFile;
import com.example.demoworkflow.vo.CFileVO;

import java.util.List;

public interface CFileService {
    void addCFile(CFileVO vo);

    void addCFile(String name, String workspace, String uuid);

    void deleteCFile(CFileVO vo);

    void deleteCFile(String workspace, String uuid);

    List<CFile> getAllCFiles();

    CFile getCFileByWorkspaceAndUuid(String workspace, String uuid);
}
