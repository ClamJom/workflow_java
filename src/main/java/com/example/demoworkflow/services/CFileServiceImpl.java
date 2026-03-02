package com.example.demoworkflow.services;

import com.example.demoworkflow.mapper.CFileMapper;
import com.example.demoworkflow.pojo.CFile;
import com.example.demoworkflow.repository.CFileRepository;
import com.example.demoworkflow.vo.CFileVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class CFileServiceImpl implements CFileService{
    @Resource
    private CFileRepository cFileRepository;

    @Override
    public CFile getCFileByWorkspaceAndUuid(String workspace, String uuid) {
        return cFileRepository.getCFilesByWorkspaceAndUuid(workspace, uuid);
    }

    @Override
    public void deleteCFile(CFileVO vo) {
        cFileRepository.deleteAllById(vo.id);
    }

    @Override
    public void deleteCFile(String workspace, String uuid){
        CFile cFile = getCFileByWorkspaceAndUuid(workspace, uuid);
        if(cFile == null) return;
        cFileRepository.delete(cFile);
    }

    @Override
    public List<CFile> getAllCFiles() {
        return cFileRepository.findAll();
    }

    @Override
    @Deprecated
    public void addCFile(CFileVO vo) {}

    @Override
    public void addCFile(String name, String workspace, String uuid) {
        CFile cFile = CFile.builder()
                .name(name)
                .workspace(workspace)
                .uuid(uuid)
                .created(new Date())
                .build();
        cFileRepository.save(cFile);
    }
}
