package com.example.demoworkflow.utils.workflow.states;

public class NodeStates {
    public static final int NULL = -1;
    /** 就绪 **/
    public static final int STAND_BY = 0x000;
    /** 出错 **/
    public static final int ERROR = 0x001;
    /** 运行中 **/
    public static final int RUNNING = 0x002;
    /** 运行完毕 **/
    public static final int DONE = 0x100;
    /** 失能，可能该节点所在的分支没有生效 **/
    public static final int DISABLED = 0x200;
}
