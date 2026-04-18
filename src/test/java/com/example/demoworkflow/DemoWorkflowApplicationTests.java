package com.example.demoworkflow;

import com.alibaba.fastjson2.JSON;
import com.example.demoworkflow.mapper.ConfigMapper;
import com.example.demoworkflow.pojo.Config;
import com.example.demoworkflow.utils.fs.FSUtilsBase;
import com.example.demoworkflow.utils.workflow.handler.WorkflowHandler;
import com.example.demoworkflow.utils.workflow.dto.Workflow;
import com.example.demoworkflow.utils.workflow.pool.GlobalPool;
import com.example.demoworkflow.utils.workflow.result.WorkflowResult;
import com.example.demoworkflow.utils.workflow.states.ResultHandlerStates;
import com.example.demoworkflow.vo.ConfigVO;
import com.example.demoworkflow.vo.WorkflowVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.HashMap;
import java.util.Map;

@EnableAsync
@SpringBootTest
class DemoWorkflowApplicationTests {

    @Autowired
    private GlobalPool globalPool;

    @Autowired
    private WorkflowHandler workflowHandler;

    @Test
    void configConvertTest(){
        ConfigVO vo = ConfigVO.builder()
                .k(10)
                .name("Test")
                .quantize(1)
                .min(2)
                .type("String")
                .value("Test")
                .build();
        Config config = ConfigMapper.INSTANCE.configVOToConfig(vo);
        System.out.println(JSON.toJSONString(config));
    }

    @Test
    void redisTest(){
        globalPool.put("Test", "foo", 1);
        Object _val = globalPool.get("Test", "foo");
        System.out.println(_val);
        globalPool.delete("Test", "foo");
        for(int i = 0; i < 5; i++){
            Map<String, String> map = new HashMap<>();
            map.put("val", String.valueOf(i));
            globalPool.put("Test", "I_" + i, map);
        }
        Map<Object, Object> pool = globalPool.getAll("Test");
        System.out.println(JSON.toJSONString(pool));
        globalPool.deleteAll("Test");
    }

    @Test
    void fsTest(){
        FSUtilsBase fs = new FSUtilsBase();
        try {
            fs.write("test", "test");
            fs.update("test", "test2");
            String content = fs.read("test");
            System.out.println(content);
//            fs.delete("test");
            fs.deleteWorkDirectory();
        }catch(Exception e){
            System.out.println(e.getMessage());
            for(var s : e.getStackTrace()){
                System.out.println(s.toString());
            }
        }
    }

    @Async("workflow")
    void foo1() throws InterruptedException {
        for(int i = 0;i < 10; i++){
            System.out.println("T1-" + i);
            Thread.sleep(100);
        }
    }

    @Async("workflow")
    void foo2() throws InterruptedException {
        for(int i = 0;i < 10; i++){
            System.out.println("T2-" + i);
            Thread.sleep(100);
        }
    }

    @Test
    void virtualThreadTest() throws InterruptedException{
        Thread t1 = Thread.ofVirtual().start(()->{
            try {
                foo1();
            }catch(Exception e){
                System.out.println(e.getMessage());
            }
        });
        Thread t2 = Thread.ofVirtual().start(()->{
            try {
                foo2();
            }catch(Exception e){
                System.out.println(e.getMessage());
            }
        });
        t1.join();
        t2.join();
    }

    private void workflowTest(String workflowJSON){
        WorkflowVO workflowVO = JSON.parseObject(workflowJSON, WorkflowVO.class);
        Workflow workflow = Workflow.castFromVO(workflowVO, globalPool);
        String token = workflow.getToken();
        System.out.println("Token: "+token);
        workflowHandler.handler(workflow, null);
        while(true) {
            int resultHandlerState = globalPool.getResultHandlerState(token);
            if(workflow.isEnded() && ((resultHandlerState & ResultHandlerStates.DONE) != 0 && (resultHandlerState & ResultHandlerStates.ERROR) != 0)){
                break;
            }
        }
    }

    @Test
    void workflowTest() throws InterruptedException {
        String workflowString = """
        {
          "name": "Test",
          "nodes": [
            {
              "id": "test-start",
              "name": "Start",
              "type": 1
            },
            {
              "id": "test-node-1",
              "name": "Hello",
              "type": 3,
              "configs":[
                {
                  "id": 1,
                  "name": "message",
                  "type": "String",
                  "value": "Hey!",
                  "k": 1,
                  "quantize": 0
                }
              ]
            },
            {
              "id": "test-end",
              "name": "End",
              "type": 2
            }
          ],
          "edges":[
              {
                "from": "test-start",
                "to": "test-node-1"
              },
              {
                "from": "test-node-1",
                "to": "test-end"
              }
          ]
        }
        """;
        workflowTest(workflowString);
    }

    @Test
    public void multiBranchTest(){
        String workflowJSON = """
                {
                   "name": "Test",
                   "nodes": [
                     {
                       "id": "test-start",
                       "name": "Start",
                       "type": 1
                     },
                     {
                       "id": "test-node-1",
                       "name": "Hello",
                       "type": 3,
                       "configs":[
                         {
                           "id": 1,
                           "name": "message",
                           "type": "String",
                           "value": "Node1",
                           "k": 1,
                           "quantize": 0
                         }
                       ]
                     },
                     {
                       "id": "test-node-2",
                       "name": "Hello",
                       "type": 3,
                       "configs":[
                         {
                           "id": 2,
                           "name": "message",
                           "type": "String",
                           "value": "Node2",
                           "k": 1,
                           "quantize": 0
                         }
                       ]
                     },
                     {
                       "id": "test-node-3",
                       "name": "Hello",
                       "type": 3,
                       "configs":[
                         {
                           "id": 3,
                           "name": "message",
                           "type": "String",
                           "value": "Node3",
                           "k": 1,
                           "quantize": 0
                         }
                       ]
                     },
                     {
                       "id": "test-node-4",
                       "name": "Hello",
                       "type": 3,
                       "configs":[
                         {
                           "id": 3,
                           "name": "message",
                           "type": "String",
                           "value": "Hey!",
                           "k": 1,
                           "quantize": 0
                         }
                       ]
                     },
                     {
                       "id": "test-end",
                       "name": "End",
                       "type": 2,
                       "configs": [
                         {
                           "id": 4,
                           "name": "output",
                           "type": "List",
                           "value": "[\\"test-node-1:message\\"]"
                         }
                       ]
                     }
                   ],
                   "edges":[
                     {
                       "from": "test-start",
                       "to": "test-node-1"
                     },
                     {
                       "from": "test-start",
                       "to": "test-node-2"
                     },
                     {
                       "from": "test-node-1",
                       "to": "test-node-4"
                     },
                     {
                       "from": "test-node-2",
                       "to": "test-node-3"
                     },
                     {
                       "from": "test-node-3",
                       "to": "test-node-4"
                     },
                     {
                       "from": "test-node-4",
                       "to": "test-end"
                     }
                   ]
                 }
        """;
        workflowTest(workflowJSON);
    }

    @Test
    void workflowConditionTest(){
        String workflowJSON = """
        {
          "name": "Test",
          "nodes": [
            {
              "id": "test-start",
              "name": "Start",
              "type": 1,
              "configs":[
                {
                  "id": 0,
                  "name": "var1",
                  "type": "Number",
                  "value": "0",
                  "k": 1,
                  "quantize": 0
                },
                {
                  "id": 0,
                  "name": "var2",
                  "type": "Number",
                  "value": "0",
                  "k": 1,
                  "quantize": 0
                }
              ]
            },
            {
              "id": "test-node-1",
              "name": "Hello",
              "type": 4,
              "configs":[
                {
                  "id": 1,
                  "name": "condition1",
                  "type": "Condition",
                  "value": "{\\"operator\\":\\"==\\",\\"a\\":\\"{{test-start:var1}}\\",\\"b\\":\\"{{test-start:var2}}\\",\\"nextNodes\\":[\\"test-node-2\\"]}",
                  "k": 1,
                  "quantize": 0
                },
                {
                  "id": 1,
                  "name": "condition2",
                  "type": "Condition",
                  "value": "{\\"operator\\":\\"!=\\",\\"a\\":\\"{{test-start:var1}}\\",\\"b\\":\\"{{test-start:var2}}\\",\\"nextNodes\\":[\\"test-node-3\\"]}",
                  "k": 1,
                  "quantize": 0
                }
              ]
            },
            {
              "id": "test-node-2",
              "name": "Hello",
              "type": 3,
              "configs":[
                {
                  "id": 2,
                  "name": "message",
                  "type": "String",
                  "value": "Node2",
                  "k": 1,
                  "quantize": 0
                }
              ]
            },
            {
              "id": "test-node-3",
              "name": "Hello",
              "type": 3,
              "configs":[
                {
                  "id": 3,
                  "name": "message",
                  "type": "String",
                  "value": "Node3",
                  "k": 1,
                  "quantize": 0
                }
              ]
            },
            {
              "id": "test-end",
              "name": "End",
              "type": 2,
              "configs": [
                {
                  "id": 4,
                  "name": "output",
                  "type": "List",
                  "value": "[\\"{{test-node-2:message}}\\"]"
                }
              ]
            }
          ],
          "edges":[
            {
              "from": "test-start",
              "to": "test-node-1"
            },
            {
              "from": "test-node-1",
              "to": "test-node-2"
            },
            {
              "from": "test-node-1",
              "to": "test-node-3"
            },
            {
              "from": "test-node-3",
              "to": "test-end"
            },
            {
              "from": "test-node-2",
              "to": "test-end"
            }
          ]
        }
        """;
        workflowTest(workflowJSON);
    }

    @Test
    void loopNodeTest(){
        String workflowJSON = """
        {
          "name": "Test",
          "nodes": [
            {
              "id": "test-start",
              "name": "Start",
              "type": 1
            },
            {
              "id": "test-loop-node",
              "name": "loop",
              "type": 65543,
              "configs":[
                {
                  "id": 1,
                  "name": "loop",
                  "type": "Number",
                  "value": "10",
                  "k": 1,
                  "quantize": 0
                }
              ]
            },
            {
              "id": "test-start-sub",
              "name": "Start",
              "type": 1,
              "parent": "test-loop-node"
            },
            {
                "id": "test-node-1",
                "name": "hello",
                "type": 3,
                "parent": "test-loop-node",
                "configs":[
                {
                    "id": 2,
                    "name": "message",
                    "type": "String",
                    "value": "Hello, World!",
                }
                ]
            },
            {
              "id": "test-end-sub",
              "name": "End",
              "type": 2,
              "parent": "test-loop-node"
            }
            {
              "id": "test-end",
              "name": "End",
              "type": 2
            }
          ],
          "edges":[{
            "from": "test-start",
            "to": "test-loop-node",
          },{
            "from": "test-loop-node",
            "to": "test-end",
          },{
            "from": "test-start-sub",
            "to": "test-node-1",
          },{
            "from": "test-node-1",
            "to": "test-end-sub",
          }]
        }
        """;
        workflowTest(workflowJSON);
    }

    @Test
    void mapConfigTest(){
        Map<String, Object> map = new HashMap<>();
        Map<String, String> subMap = new HashMap<>();
        subMap.put("b", "test");
        map.put("a", subMap);
        globalPool.put("test", "test:mapConfig", map);
        String ret = globalPool.parseConfig("{{test:mapConfig}}", "test");
        assert ret.equals("{\"a\":{\"b\":\"test\"}}");
    }
}
