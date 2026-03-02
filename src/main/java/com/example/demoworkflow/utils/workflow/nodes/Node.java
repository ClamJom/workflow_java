package com.example.demoworkflow.utils.workflow.nodes;

public interface Node {
    void before();

    void run();

    void after();
}
