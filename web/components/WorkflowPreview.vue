<script setup>
import {ref, shallowRef, computed, watch, onMounted, onUnmounted, nextTick} from 'vue';
import {
    VueFlow,
    useVueFlow,
    MarkerType,
} from '@vue-flow/core';
import {Controls} from '@vue-flow/controls';
import {MiniMap} from '@vue-flow/minimap';
import {
    Button,
    message,
    Tooltip,
    Divider,
    Dropdown,
    Menu,
    Input,
    Drawer,
} from 'ant-design-vue';
import {
    PlusOutlined,
    SaveOutlined,
    ColumnWidthOutlined,
    ColumnHeightOutlined,
    PlayCircleOutlined,
} from '@ant-design/icons-vue';

import {nodeTypes, getVueFlowNodeType, NODE_TYPE_CODE} from './nodes/index.js';
import NodeConfigPanel from './NodeConfigPanel.vue';
import {autoLayoutVueFlow} from '../utils/layout.js';
import api from '../api/index.js';
import {generateUUID} from '../utils/token.js';

import '@vue-flow/core/dist/style.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';

// ─── Props ───────────────────────────────────────────────────────────────────

const props = defineProps({
    /**
     * 工作流 UUID，组件根据此值加载并展示工作流
     */
    uuid: {
        type: String,
        required: true
    }
});

const emit = defineEmits(['saved']);

// ─── Vue-Flow 实例 ────────────────────────────────────────────────────────────

const {
    addNodes,
    addEdges,
    removeNodes,
    removeEdges,
    getNodes,
    getEdges,
    onConnect,
    setNodes,
    setEdges,
    findNode,
    getSelectedNodes,
    getSelectedEdges,
    fitView,
} = useVueFlow();

// ─── 状态 ─────────────────────────────────────────────────────────────────────

/** Vue-Flow 节点列表 */
const nodes = ref([]);
/** Vue-Flow 边列表 */
const edges = ref([]);
/** 当前布局方向 */
const layoutDirection = ref('horizontal');
/** 加载状态 */
const loading = ref(false);
/** 工作流名称 */
const workflowName = ref('');
/**
 * 当前选中的节点 ID（使用 shallowRef 避免将 Vue-Flow 响应式节点对象深度响应化）
 * 存储 ID 而非节点对象，通过 findNode 按需获取最新节点数据
 */
const selectedNodeId = shallowRef(null);
/** 配置面板是否可见 */
const configDrawerVisible = ref(false);
/** 节点类型列表（从后端获取） */
const nodeTypeList = ref([]);
/** 保存中状态 */
const saving = ref(false);
/** 配置侧边栏宽度（px） */
const configPanelWidth = ref(360);
/** 是否正在拖拽调整侧边栏宽度 */
let isDraggingPanel = false;
/** 拖拽开始时的鼠标 X 坐标 */
let dragStartX = 0;
/** 拖拽开始时的侧边栏宽度 */
let dragStartWidth = 0;
/** 是否正在加载 */
let isLoading = false;
/** 是否正在更新节点配置（防止 onConfigsChange 递归调用） */
let isUpdatingConfigs = false;

/** 运行工作流中（SSE 连接未结束） */
const runningWorkflow = ref(false);
/** 运行日志 Drawer */
const runDrawerOpen = ref(false);
/** @type {import('vue').Ref<Array<{t: number, msg: string, state?: number, nodeId?: string, raw?: string}>>} */
const runLogEntries = ref([]);
/** 中止当前 SSE 连接 */
let runAbortController = null;
const runLogBodyRef = ref(null);

// ─── 计算属性 ─────────────────────────────────────────────────────────────────

/**
 * 当前选中节点可用的变量池（由上游节点类型调用 getNodeOutputs 生成）
 */
const selectedNodePool = ref([]);

/** 按节点类型缓存 /api/v3/node/{code}/output，避免重复请求 */
const nodeOutputsByTypeCache = new Map();

/**
 * 起始节点：运行时将 Map 配置「input」中每个键写入 nodePool（见 StartNode.run），
 * 变量池应展示各键名，而非后端 getNodeOutputs 里名为 input 的整段 Map。
 * @param {Object} wnode - 节点 data.wnode
 * @returns {Object[]} Map 的键名列表
 */
function getStartNodeMapKeys(wnode) {
    const configs = wnode?.configs;
    return configs.map(item => {
      return {
        name: item["name"],
        des: item["des"],
        type: item["type"]
      }
    });
}

/**
 * 画布拓扑签名（节点 id+类型、边；起始节点另含 Map「input」的键列表），用于刷新变量池
 */
const graphTopologySig = computed(() => {
    const ns = getNodes.value.map(n => {
        const typ = n.data?.wnode?.type ?? '';
        if (typ === NODE_TYPE_CODE.START) {
            const keys = getStartNodeMapKeys(n.data?.wnode).sort().join(',');
            return `${n.id}:${typ}:${keys}`;
        }
        return `${n.id}:${typ}`;
    }).sort().join(',');
    const es = getEdges.value.map(e => `${e.source}-${e.target}`).sort().join(',');
    return `${ns}|${es}`;
});

async function refreshSelectedNodePool() {
    const nodeId = selectedNodeId.value;
    if (!nodeId) {
        selectedNodePool.value = [];
        return;
    }
    try {
        selectedNodePool.value = await buildUpstreamPoolAsync(nodeId);
    } catch (err) {
        console.error('[WorkflowPreview] 上游变量池加载失败', err);
        selectedNodePool.value = [];
    }
}

watch([selectedNodeId, graphTopologySig], () => {
    refreshSelectedNodePool();
}, {immediate: true});

/**
 * 当前选中节点的 wnode 数据快照（用于传递给 NodeConfigPanel）
 * 使用 computed 而非直接存储节点对象，避免响应式循环
 */
const selectedNodeData = computed(() => {
    if (!selectedNodeId.value) return null;
    const node = findNode(selectedNodeId.value);
    return node ? node.data?.wnode : null;
});

// ─── 工具函数 ─────────────────────────────────────────────────────────────────

/**
 * 将后端 WorkflowVO 转换为 Vue-Flow 格式
 * @param {Object} workflowVO - 后端返回的工作流数据
 * @param {'horizontal'|'vertical'} direction - 布局方向
 * @returns {{ nodes: Array, edges: Array }}
 */
function castToVueFlow(workflowVO, direction) {
    const wnodes = workflowVO.nodes || [];
    const wedges = workflowVO.edges || [];

    // 检查是否所有节点都有位置信息
    const needLayout = wnodes.some(n => !n.position);

    let vfNodes = wnodes.map(n => ({
        id: n.id,
        type: getVueFlowNodeType(n.type),
        position: n.position || {x: 0, y: 0},
        data: {wnode: {...n}},
        label: n.name || '',
        parentNode: n.parent || ''
    }));

    const vfEdges = wedges.map(e => ({
        id: `${e.from}-${e.to}-${generateUUID()}`,
        source: e.from,
        target: e.to,
        markerEnd: MarkerType.ArrowClosed,
        ...(e.fromHandle ? {sourceHandle: e.fromHandle} : {}),
    }));

    // 若需要自动布局，则计算位置
    if (needLayout) {
        vfNodes = autoLayoutVueFlow(vfNodes, vfEdges, direction);
    }

    return {nodes: vfNodes, edges: vfEdges};
}

/**
 * 将 Vue-Flow 格式转换回后端 WorkflowVO
 * @returns {Object} WorkflowVO
 */
function castToWorkflowVO() {
    const currentNodes = getNodes.value;
    const currentEdges = getEdges.value;

    return {
        name: workflowName.value,
        nodes: currentNodes.map(n => ({
            id: n.id,
            name: n.data?.wnode?.name || '',
            type: n.data?.wnode?.type || 0,
            configs: n.data?.wnode?.configs || [],
            position: n.position,
            parent: n.parentNode
        })),
        edges: currentEdges.map(e => ({
            from: e.source,
            to: e.target,
            ...(e.sourceHandle != null && e.sourceHandle !== ''
                ? {fromHandle: e.sourceHandle}
                : {}),
        })),
    };
}

/**
 * 运行开始前清除所有节点的运行态高亮
 */
function clearNodeRunStates() {
    setNodes(getNodes.value.map(n => ({
        ...n,
        data: {
            ...n.data,
            runState: undefined,
            precheckError: undefined,
        },
    })));
}

/**
 * 预检失败时在画布上标红问题节点
 * @param {string[]} ids
 */
function applyPrecheckHighlight(ids) {
    const idSet = new Set(ids || []);
    setNodes(getNodes.value.map(n => ({
        ...n,
        data: {
            ...n.data,
            precheckError: idSet.has(n.id),
        },
    })));
}

/**
 * 从 WorkflowResult 解析画布节点 ID（节点消息里 from 会被设为 nodeId）
 */
function resolveNodeIdFromResult(data) {
    if (data.nodeId) return data.nodeId;
    if (data.from && data.from !== 'system') return data.from;
    return null;
}

/**
 * 将 SSE 中的节点状态同步到 Vue Flow 节点 data.runState
 */
function applyWorkflowResultToNodes(data) {
    const nodeId = resolveNodeIdFromResult(data);
    if (!nodeId || !findNode(nodeId)) return;
    if (typeof data.state !== 'number') return;
    setNodes(getNodes.value.map(n => {
        if (n.id !== nodeId) return n;
        return {
            ...n,
            data: {
                ...n.data,
                runState: data.state,
            },
        };
    }));
}

/**
 * 运行前校验工作流图结构（与后端 Workflow.castFromVO 约束一致）
 * @param {{ nodes: Array<{id: string, name?: string, type: number}>, edges: Array<{from: string, to: string}> }} payload
 * @returns {{ valid: boolean, message: string, invalidNodeIds: string[] }}
 */
function preCheckWorkflow(payload) {
    const fail = (message, invalidNodeIds = []) => ({
        valid: false,
        message,
        invalidNodeIds,
    });
    const ok = () => ({valid: true, message: '', invalidNodeIds: []});

    const nodes = payload?.nodes || [];
    const edges = payload?.edges || [];

    if (nodes.length === 0) {
        return fail('工作流中没有任何节点', []);
    }

    const nodeIds = new Set(nodes.map(n => n.id));
    const starts = nodes.filter(n => n.type === NODE_TYPE_CODE.START);
    const ends = nodes.filter(n => n.type === NODE_TYPE_CODE.END);

    if (starts.length === 0) {
        return fail('工作流必须包含一个起始节点', []);
    }
    if (starts.length > 1) {
        return fail('工作流只能有一个起始节点', starts.map(n => n.id));
    }
    if (ends.length === 0) {
        return fail('工作流必须包含一个结束节点', []);
    }
    if (ends.length > 1) {
        return fail('工作流只能有一个结束节点', ends.map(n => n.id));
    }

    const startId = starts[0].id;
    const endId = ends[0].id;

    const outAdj = {};
    const inCount = {};
    const outCount = {};
    nodes.forEach((n) => {
        outAdj[n.id] = [];
        inCount[n.id] = 0;
        outCount[n.id] = 0;
    });

    for (const e of edges) {
        const from = e?.from;
        const to = e?.to;
        if (!from || !to) continue;
        if (!nodeIds.has(from) || !nodeIds.has(to)) continue;
        if (from === to) {
            return fail('不允许存在自环连线', [from]);
        }
        outAdj[from].push(to);
        inCount[to]++;
        outCount[from]++;
    }

    const badIn = [];
    for (const n of nodes) {
        if (n.id === startId) continue;
        if (inCount[n.id] < 1) badIn.push(n.id);
    }
    if (badIn.length) {
        const labels = badIn.slice(0, 3).map((id) => {
            const node = nodes.find(x => x.id === id);
            return node?.name || id;
        }).join('、');
        const more = badIn.length > 3 ? ` 等共 ${badIn.length} 个` : '';
        return fail(
            `以下节点没有入边（除起始节点外均需有入度）：${labels}${more}`,
            badIn,
        );
    }

    const badOut = [];
    for (const n of nodes) {
        if (n.id === endId) continue;
        if (outCount[n.id] < 1) badOut.push(n.id);
    }
    if (badOut.length) {
        const labels = badOut.slice(0, 3).map((id) => {
            const node = nodes.find(x => x.id === id);
            return node?.name || id;
        }).join('、');
        const more = badOut.length > 3 ? ` 等共 ${badOut.length} 个` : '';
        return fail(
            `以下节点没有出边（除结束节点外均需有出度）：${labels}${more}`,
            badOut,
        );
    }

    const inDeg = {};
    nodes.forEach((n) => {
        inDeg[n.id] = 0;
    });
    edges.forEach((e) => {
        if (nodeIds.has(e.from) && nodeIds.has(e.to)) {
            inDeg[e.to]++;
        }
    });

    const queue = [];
    nodes.forEach((n) => {
        if (inDeg[n.id] === 0) queue.push(n.id);
    });

    const processed = new Set();
    while (queue.length > 0) {
        const u = queue.shift();
        processed.add(u);
        for (const v of outAdj[u] || []) {
            inDeg[v]--;
            if (inDeg[v] === 0) queue.push(v);
        }
    }

    const cycleNodes = nodes.filter(n => !processed.has(n.id)).map(n => n.id);
    if (cycleNodes.length) {
        const labels = cycleNodes.slice(0, 3).map((id) => {
            const node = nodes.find(x => x.id === id);
            return node?.name || id;
        }).join('、');
        const more = cycleNodes.length > 3 ? ` 等共 ${cycleNodes.length} 个` : '';
        return fail(`工作流存在环结构，涉及节点：${labels}${more}`, cycleNodes);
    }

    return ok();
}

/**
 * 使用当前画布运行工作流（SSE），并在 Drawer 中展示日志
 */
async function handleRunWorkflow() {
    if (runningWorkflow.value) return;
    runAbortController?.abort();
    runAbortController = new AbortController();
    const signal = runAbortController.signal;

    clearNodeRunStates();
    runLogEntries.value = [];
    runDrawerOpen.value = true;

    const payload = castToWorkflowVO();
    const preCheck = preCheckWorkflow(payload);
    if (!preCheck.valid) {
        message.error(preCheck.message);
        applyPrecheckHighlight(preCheck.invalidNodeIds || []);
        runDrawerOpen.value = false;
        return;
    }

    runningWorkflow.value = true;

    try {
        await api.workflow.runWorkflow(payload, {
            signal,
            async onopen(response) {
                if (response.ok) return;
                throw new Error(`无法建立 SSE：HTTP ${response.status}`);
            },
            onmessage(ev) {
                const text = (ev.data || '').trim();
                if (!text) return;
                try {
                    const parsed = JSON.parse(text);
                    runLogEntries.value.push({
                        t: Date.now(),
                        msg: parsed.msg ?? '',
                        state: parsed.state,
                        nodeId: resolveNodeIdFromResult(parsed) || undefined,
                        raw: text,
                    });
                    applyWorkflowResultToNodes(parsed);
                } catch {
                    runLogEntries.value.push({
                        t: Date.now(),
                        msg: text,
                        raw: text,
                    });
                }
                nextTick(() => {
                    const el = runLogBodyRef.value;
                    if (el) el.scrollTop = el.scrollHeight;
                });
            },
            onerror(err) {
                if (signal.aborted) {
                    throw err;
                }
                message.error(err?.message || '运行连接异常');
                throw err;
            },
        });
    } catch (err) {
        if (!signal.aborted) {
            console.error('[WorkflowPreview] 运行失败', err);
        }
    } finally {
        runningWorkflow.value = false;
        runAbortController = null;
    }
}

/**
 * 从当前节点沿边 **反向** 做 BFS，得到所有祖先节点（含起始节点及所有分叉上的节点），顺序为近→远。
 * @param {string} nodeId - 当前节点
 * @param {Array<{source: string, target: string}>} edges - 画布边
 * @returns {string[]} 祖先节点 id 列表（不含 nodeId 自身）
 */
function collectUpstreamNodeIdsOrdered(nodeId, edges) {
    const reverseAdj = {};
    edges.forEach(e => {
        if (!reverseAdj[e.target]) reverseAdj[e.target] = [];
        reverseAdj[e.target].push(e.source);
    });

    const visited = new Set();
    const order = [];
    const queue = [nodeId];
    while (queue.length > 0) {
        const cur = queue.shift();
        if (visited.has(cur)) continue;
        visited.add(cur);
        if (cur !== nodeId) {
            order.push(cur);
        }
        const parents = reverseAdj[cur] || [];
        parents.forEach(p => {
            if (!visited.has(p)) queue.push(p);
        });
    }
    return order;
}

/**
 * 构建指定节点的上游变量池：遍历至所有祖先（含起始节点），汇总各节点可暴露变量
 * @param {string} nodeId - 目标节点 ID
 * @returns {Promise<Array<{name: string, des: string, type: string}>>}
 */
async function buildUpstreamPoolAsync(nodeId) {
    const currentEdges = getEdges.value;
    const currentNodes = getNodes.value;

    const upstreamOrder = collectUpstreamNodeIdsOrdered(nodeId, currentEdges);

    const pool = [];
    for (const upstreamId of upstreamOrder) {
        const node = currentNodes.find(n => n.id === upstreamId);
        if (!node) continue;
        const nodeCode = node.data?.wnode?.type;
        /** 仅跳过无类型节点；0 为合法枚举值需保留 */
        if (nodeCode == null) continue;

        if (nodeCode === NODE_TYPE_CODE.START) {
            const variables = getStartNodeMapKeys(node.data?.wnode);
            variables.forEach((item) => {
                pool.push({
                    name: `${upstreamId}:${item["name"]}`,
                    des: item["des"],
                    type: item["type"],
                });
            });
            continue;
        }

        let outputs = nodeOutputsByTypeCache.get(nodeCode);
        if (outputs === undefined) {
            const res = await api.workflow.getNodeOutputs(nodeCode);
            outputs = res.data || [];
            nodeOutputsByTypeCache.set(nodeCode, outputs);
        }

        outputs.forEach(out => {
            pool.push({
                name: `${upstreamId}:${out.name}`,
                des: out.des || out.name,
                type: out.type || 'String',
            });
        });
    }
    return pool;
}

// ─── 加载工作流 ───────────────────────────────────────────────────────────────

async function loadWorkflow() {
    if (!props.uuid) return;
    isLoading = true;
    loading.value = true;

    try {
        const res = await api.workflow.getWorkflow(props.uuid);
        const workflowVO = res.data;
        workflowName.value = workflowVO.name || '';

        nodeOutputsByTypeCache.clear();

        const {nodes: vfNodes, edges: vfEdges} = castToVueFlow(workflowVO, layoutDirection.value);
        nodes.value = vfNodes;
        edges.value = vfEdges;

        await nextTick();
        fitView({padding: 0.2});
        await nextTick();
        syncConditionNextNodesFromEdges();
    } catch (err) {
        message.error(`加载工作流失败：${err?.message || '未知错误'}`);
        console.error('[WorkflowPreview] 加载失败', err);
    } finally {
        loading.value = false;
        // 延迟重置 isLoading，防止 watch 在 setNodes/setEdges 后立即触发保存
        setTimeout(() => {
            isLoading = false;
        }, 500);
    }
}

/**
 * 加载节点类型列表
 */
async function loadNodeTypes() {
    try {
        const res = await api.workflow.getNodeTypes();
        nodeTypeList.value = res.data || [];
    } catch (err) {
        console.error('[WorkflowPreview] 加载节点类型失败', err);
    }
}

// ─── 手动保存 ─────────────────────────────────────────────────────────────────

/**
 * 手动保存工作流
 */
async function saveWorkflow() {
    if (saving.value) return;
    saving.value = true;
    try {
        const workflowVO = castToWorkflowVO();
        await api.workflow.updateWorkflow(workflowVO, props.uuid);
        message.success('保存成功');
        emit('saved', {uuid: props.uuid, name: workflowName.value});
    } catch (err) {
        message.error(`保存失败：${err?.message || '未知错误'}`);
        console.error('[WorkflowPreview] 保存失败', err);
    } finally {
        saving.value = false;
    }
}

// ─── 节点操作 ─────────────────────────────────────────────────────────────────

/**
 * 添加新节点
 * @param {Object} nodeType - 节点类型对象 { code, name, type }
 */
function addNode(nodeType) {
    const code = nodeType.code;

    // StartNode/EndNode 唯一性约束
    if (code === NODE_TYPE_CODE.START) {
        const existing = getNodes.value.find(n => n.data?.wnode?.type === NODE_TYPE_CODE.START);
        if (existing) {
            message.warning('每个工作流只允许拥有一个起始节点');
            return;
        }
    }
    if (code === NODE_TYPE_CODE.END) {
        const existing = getNodes.value.find(n => n.data?.wnode?.type === NODE_TYPE_CODE.END);
        if (existing) {
            message.warning('每个工作流只允许拥有一个结束节点');
            return;
        }
    }

    const nodeId = generateUUID();
    const newNode = {
        id: nodeId,
        type: getVueFlowNodeType(code),
        // 新节点放置在画布中心偏右下方
        position: {
            x: 100 + Math.random() * 200,
            y: 100 + Math.random() * 200,
        },
        data: {
            wnode: {
                id: nodeId,
                name: nodeType.name,
                type: code,
                configs: [],
            },
            outputs: [],
        },
        label: nodeType.name,
    };

    addNodes([newNode]);
}

/**
 * 根据画布边更新条件节点：每条 type===Condition 的配置对应一个分支，source-{i} 与配置出现顺序一致
 */
function syncConditionNextNodesFromEdges() {
    const edgeList = getEdges.value;
    const nodeList = getNodes.value;

    const updated = nodeList.map((node) => {
        if (node.data?.wnode?.type !== NODE_TYPE_CODE.CONDITION) return node;
        const cfgList = node.data.wnode.configs || [];
        const conditionIndices = cfgList
            .map((c, i) => (c.type === 'Condition' ? i : -1))
            .filter((i) => i >= 0);
        if (conditionIndices.length === 0) return node;

        const outgoing = edgeList.filter(e => e.source === node.id);
        const byHandle = {};
        outgoing.forEach((e) => {
            const h = e.sourceHandle || 'source-0';
            if (!byHandle[h]) byHandle[h] = [];
            byHandle[h].push(e.target);
        });

        const newConfigs = [...cfgList];
        let anyChange = false;

        conditionIndices.forEach((cfgIndex, branchIndex) => {
            const cfg = newConfigs[cfgIndex];
            const h = `source-${branchIndex}`;
            let obj;
            try {
                obj = JSON.parse(cfg.value || '{}');
            } catch {
                return;
            }
            if (Array.isArray(obj)) return;
            const targets = [...new Set(byHandle[h] || [])];
            const nextObj = {...obj, nextNodes: targets};
            const nextVal = JSON.stringify(nextObj);
            if (nextVal !== cfg.value) {
                newConfigs[cfgIndex] = {...cfg, value: nextVal};
                anyChange = true;
            }
        });

        if (!anyChange) return node;

        return {
            ...node,
            data: {
                ...node.data,
                wnode: {
                    ...node.data.wnode,
                    configs: newConfigs,
                },
            },
        };
    });

    if (updated.every((n, i) => n === nodeList[i])) return;
    setNodes(updated);
}

// ─── 连接处理 ─────────────────────────────────────────────────────────────────

onConnect((params) => {
    // 禁止自指
    if (params.source === params.target) {
        message.warning('节点不允许连接到自身');
        return;
    }

    addEdges([{
        ...params,
        id: `${params.source}-${params.target}-${generateUUID()}`,
        markerEnd: MarkerType.ArrowClosed,
    }]);
    nextTick(() => {
        syncConditionNextNodesFromEdges();
    });
});

// ─── 节点点击 ─────────────────────────────────────────────────────────────────

function onNodeClick({node}) {
    // 只存储节点 ID，避免将 Vue-Flow 内部响应式节点对象存入 ref 导致循环
    selectedNodeId.value = node.id;
    configDrawerVisible.value = true;
}

/**
 * 配置变更时更新节点数据
 * 通过 setNodes 整体替换节点列表，避免直接修改 Vue-Flow 内部响应式对象
 * @param {Array} configs - 最新配置数组
 */
function onConfigsChange(configs) {
    if (!selectedNodeId.value) return;
    // 防止递归：若当前正在更新配置，则忽略此次调用
    if (isUpdatingConfigs) return;
    isUpdatingConfigs = true;
    try {
        // 使用 setNodes 更新，避免直接修改响应式节点对象引发循环
        setNodes(getNodes.value.map(n => {
            if (n.id !== selectedNodeId.value) return n;
            return {
                ...n,
                data: {
                    ...n.data,
                    wnode: {
                        ...n.data?.wnode,
                        configs,
                    },
                },
            };
        }));
    } finally {
        // 使用 nextTick 延迟重置，确保 Vue 完成本轮更新后再允许下一次调用
        nextTick(() => {
            isUpdatingConfigs = false;
        });
    }
}

// ─── 布局切换 ─────────────────────────────────────────────────────────────────

function applyLayout(direction) {
    layoutDirection.value = direction;
    const currentNodes = getNodes.value;
    const currentEdges = getEdges.value;
    const laid = autoLayoutVueFlow(currentNodes, currentEdges, direction);
    setNodes(laid);
    nextTick(() => fitView({padding: 0.2}));
}

// ─── 键盘快捷键 ───────────────────────────────────────────────────────────────

function onKeyDown(e) {
    // 忽略输入框中的按键
    const tag = document.activeElement?.tagName?.toLowerCase();
    if (tag === 'input' || tag === 'textarea') return;

    if (e.key === 'Delete' || e.key === 'Backspace') {
        const selNodes = getSelectedNodes.value;
        const selEdges = getSelectedEdges.value;
        if (selNodes.length > 0) removeNodes(selNodes);
        if (selEdges.length > 0) removeEdges(selEdges);
    }
}

// ─── 节点/边变化监听（触发自动保存） ─────────────────────────────────────────

function onNodesChange(changes) {
    // 位置变化时触发保存
    // const hasMoveChange = changes.some(c => c.type === 'position' && !c.dragging);
    // if (hasMoveChange) scheduleSave();
}

function onEdgesChange(_changes) {
    nextTick(() => {
        syncConditionNextNodesFromEdges();
    });
}

// ─── 生命周期 ─────────────────────────────────────────────────────────────────

// ─── 侧边栏拖拽调整宽度 ───────────────────────────────────────────────────────

const MIN_PANEL_WIDTH = 240;
const MAX_PANEL_WIDTH = 800;

/**
 * 开始拖拽调整侧边栏宽度
 * @param {MouseEvent} e
 */
function onResizeHandleMouseDown(e) {
    isDraggingPanel = true;
    dragStartX = e.clientX;
    dragStartWidth = configPanelWidth.value;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    e.preventDefault();
}

function onDocumentMouseMove(e) {
    if (!isDraggingPanel) return;
    // 向左拖拽增大宽度（因为侧边栏在右侧）
    const delta = dragStartX - e.clientX;
    const newWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, dragStartWidth + delta));
    configPanelWidth.value = newWidth;
}

function onDocumentMouseUp() {
    if (!isDraggingPanel) return;
    isDraggingPanel = false;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
}

onMounted(() => {
    loadWorkflow();
    loadNodeTypes();
    window.addEventListener('keydown', onKeyDown);
    document.addEventListener('mousemove', onDocumentMouseMove);
    document.addEventListener('mouseup', onDocumentMouseUp);
});

onUnmounted(() => {
    runAbortController?.abort();
    runAbortController = null;
    window.removeEventListener('keydown', onKeyDown);
    document.removeEventListener('mousemove', onDocumentMouseMove);
    document.removeEventListener('mouseup', onDocumentMouseUp);
});

watch(() => props.uuid, (newUuid) => {
    runAbortController?.abort();
    runAbortController = null;
    runningWorkflow.value = false;
    if (newUuid) loadWorkflow();
});
</script>

<template>
  <div class="workflow-preview">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-title-wrap">
        <Input
          v-model:value="workflowName"
          placeholder="未命名工作流"
          class="workflow-title-input"
          :disabled="loading"
        />
      </div>

      <div class="toolbar-actions">
        <!-- 添加节点下拉菜单 -->
        <Dropdown :trigger="['click']">
          <Button type="primary" size="small">
            <PlusOutlined />
            添加节点
          </Button>
          <template #overlay>
            <Menu>
              <Menu.Item
                v-for="nt in nodeTypeList"
                :key="nt.code"
                @click="addNode(nt)"
              >
                {{ nt.name }}
              </Menu.Item>
            </Menu>
          </template>
        </Dropdown>

        <Divider type="vertical" />

        <Tooltip title="运行当前画布（未保存的修改也会参与运行）">
          <Button
            type="primary"
            ghost
            size="small"
            :loading="runningWorkflow"
            :disabled="loading"
            @click="handleRunWorkflow"
          >
            <PlayCircleOutlined />
            运行
          </Button>
        </Tooltip>

        <Divider type="vertical" />

        <!-- 保存按钮 -->
        <Tooltip title="保存工作流">
          <Button
            size="small"
            :loading="saving"
            @click="saveWorkflow"
          >
            <SaveOutlined />
            保存
          </Button>
        </Tooltip>

        <Divider type="vertical" />

        <!-- 布局切换 -->
        <Tooltip title="横向布局">
          <Button
            size="small"
            :type="layoutDirection === 'horizontal' ? 'primary' : 'default'"
            @click="applyLayout('horizontal')"
          >
            <ColumnWidthOutlined />
          </Button>
        </Tooltip>
        <Tooltip title="纵向布局">
          <Button
            size="small"
            :type="layoutDirection === 'vertical' ? 'primary' : 'default'"
            @click="applyLayout('vertical')"
          >
            <ColumnHeightOutlined />
          </Button>
        </Tooltip>
      </div>
    </div>

    <!-- Vue-Flow 画布 -->
    <div class="flow-canvas">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :node-types="nodeTypes"
        :delete-key-code="null"
        :connect-on-click="false"
        fit-view-on-init
        @node-click="onNodeClick"
        @nodes-change="onNodesChange"
        @edges-change="onEdgesChange"
      >
        <Controls />
        <MiniMap />
      </VueFlow>
    </div>

    <!-- 节点配置侧边栏（可拖拽调整宽度） -->
    <div
      v-if="configDrawerVisible"
      class="config-panel"
      :style="{ width: configPanelWidth + 'px' }"
    >
      <!-- 拖拽调整宽度的分隔条 -->
      <div
        class="resize-handle"
        @mousedown="onResizeHandleMouseDown"
      />

      <!-- 侧边栏头部 -->
      <div class="config-panel-header">
        <span class="config-panel-title">节点配置</span>
        <Button
          type="text"
          size="small"
          @click="configDrawerVisible = false"
        >
          ✕
        </Button>
      </div>

      <!-- 侧边栏内容 -->
      <div class="config-panel-body">
        <NodeConfigPanel
          v-if="selectedNodeId && selectedNodeData"
          :key="selectedNodeId"
          :node-code="selectedNodeData.type"
          :node-name="selectedNodeData.name || ''"
          :node-id="selectedNodeId"
          :initial-configs="selectedNodeData.configs"
          :pool="selectedNodePool"
          :request-pool-refresh="refreshSelectedNodePool"
          @configs-change="onConfigsChange"
        />
      </div>
    </div>

    <!-- 运行日志（SSE） -->
    <Drawer
      v-model:open="runDrawerOpen"
      title="运行日志"
      placement="right"
      :width="440"
      :z-index="200"
      :mask="false"
    >
      <div ref="runLogBodyRef" class="run-log-body">
        <div
          v-for="(entry, idx) in runLogEntries"
          :key="idx"
          class="run-log-entry"
        >
          <div class="run-log-msg">{{ entry.msg }}</div>
          <div v-if="entry.nodeId" class="run-log-meta">节点: {{ entry.nodeId }}</div>
          <div
            v-if="entry.state !== undefined && entry.state !== null"
            class="run-log-meta"
          >
            state: {{ entry.state }}
          </div>
        </div>
        <div v-if="runLogEntries.length === 0" class="run-log-empty">
          暂无输出，等待 SSE 事件…
        </div>
      </div>
    </Drawer>
  </div>
</template>

<style scoped>
.workflow-preview {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
  z-index: 10;
}

.toolbar-title-wrap {
  flex: 1;
  min-width: 0;
  max-width: 360px;
  margin-right: 16px;
}

.workflow-title-input {
  font-size: 14px;
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-canvas {
  flex: 1;
  overflow: hidden;
}

/* 确保 Vue-Flow 填满容器 */
:deep(.vue-flow) {
  width: 100%;
  height: 100%;
}

/* ── 可拖拽配置侧边栏 ── */
.config-panel {
  position: absolute;
  top: 0;
  right: 0;
  height: 100%;
  background: #fff;
  border-left: 1px solid #f0f0f0;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  z-index: 100;
  min-width: 300px;
  max-width: 800px;
}

/* 左边缘拖拽条 */
.resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
  z-index: 1;
}

.resize-handle:hover {
  background: #1677ff;
}

.config-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.config-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.run-log-body {
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  font-size: 13px;
}

.run-log-entry {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.run-log-entry:last-child {
  border-bottom: none;
}

.run-log-msg {
  color: #262626;
  word-break: break-word;
}

.run-log-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  font-family: ui-monospace, monospace;
  word-break: break-all;
}

.run-log-empty {
  color: #8c8c8c;
  padding: 16px 0;
}
</style>
