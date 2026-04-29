package com.example.demoworkflow.utils.workflow.nodes;

import com.example.demoworkflow.utils.types.ConfigTypes;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.vo.ConfigVO;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

public class SleepNode extends NodeImpl{
    public SleepNode(GlobalPool globalPool) {
        super(globalPool);
    }

    public SleepNode(GlobalPool globalPool, String nodeId) {
        super(globalPool, nodeId);
    }

    @Override
    public List<ConfigVO> getNodeConfigs(){
        return List.of(ConfigVO.builder()
                .des("休眠时间（毫秒）")
                .name("mills")
                .value("100")
                .max(10000)
                .min(100)
                .k(1)
                .type(ConfigTypes.NUMBER)
                .required(true)
                .build());
    }

    @Override
    public void run(){
        Integer mills = (Integer) configs.get("mills");
        if(mills == null) return;
        try{
            Thread.sleep(mills);
        }catch(InterruptedException e){
            throw new RuntimeException("休眠节点出错：" + this.nodeId);
        }
    }
}
