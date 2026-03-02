package com.example.demoworkflow.utils.fs.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.example.demoworkflow.pojo.CFile;
import com.example.demoworkflow.services.CFileService;
import com.example.demoworkflow.services.CFileServiceImpl;
import com.example.demoworkflow.utils.fs.FSUtilsBase;
import com.example.demoworkflow.vo.WorkflowVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
public class WorkflowFileUtils extends FSUtilsBase {
    @Autowired
    private CFileServiceImpl cFileService;

    @Override
    public void initWorkPath() throws IOException {
        Path basePath = jarPath.resolve("workflow");
        if(!Files.exists(basePath)){
            Files.createDirectory(basePath);
        }
    }

    public WorkflowFileUtils() {
        super();
        this.workPath = "workflow";
        this.suffix = ".json";
    }

    public boolean saveWorkflow(WorkflowVO workflow){
        String uuid = UUID.randomUUID().toString();
        try{
            cFileService.addCFile(workflow.name, workPath, uuid);
            write(uuid, workflow);
        }catch(Exception e){
            return false;
        }
        return true;
    }

    public boolean deleteWorkflow(String uuid){
        try{
            cFileService.deleteCFile(workPath, uuid);
            delete(uuid);
        }catch(Exception e){
            return false;
        }
        return true;
    }

    public WorkflowVO loadWorkflow(String uuid){
        try {
            return JSON.parseObject(this.read(uuid), WorkflowVO.class);
        }catch(Exception e){
            return null;
        }
    }
}
