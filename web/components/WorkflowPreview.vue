<script setup>
import {ref, shallowRef, computed, watch, onMounted, onUnmounted, nextTick} from 'vue';
import {
    VueFlow,
    useVueFlow,
    MarkerType,
    getIncomers,
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
  Switch,
  Form,
} from 'ant-design-vue';
import {
    PlusOutlined,
    SaveOutlined,
    ColumnWidthOutlined,
    ColumnHeightOutlined,
    PlayCircleOutlined,
} from '@ant-design/icons-vue';

import {nodeTypes, getVueFlowNodeType, NODE_TYPE_CODE, NESTABLE_FLAG, isNestableNodeType} from './nodes/index.js';
import NodeConfigPanel from './NodeConfigPanel.vue';
import {autoLayoutVueFlowNested, NODE_WIDTH, NODE_HEIGHT} from '../utils/layout.js';
import api from '../api/index.js';
import {generateUUID} from '../utils/token.js';

import '@vue-flow/core/dist/style.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';

/** 边默认叠放顺序，便于在子图内点到边线 */
const defaultEdgeOpts = {zIndex: 1001};

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
/** 右侧栏宽度（px），配置区与运行日志共用 */
const configPanelWidth = ref(360);

/** 与画布同级的右侧栏（节点配置 / 运行日志）是否显示 */
const showSideRail = computed(() => configDrawerVisible.value || runDrawerOpen.value);
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
/** 是否隐藏仅状态指示日志 */
const hideStateFlag = ref(true);
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
 * 变量池应展示各键名，而非后端 getNodeOutputs 里名为 input 的整段 Map。
 * @param {Object} wnode - 节点 data.wnode
 * @returns {Object[]} Map 的键名列表
 */
function getNodeMapKeys(wnode) {
    const configs = wnode?.configs || [];
    return configs.map(item => {
      return {
        name: item["name"],
        value: item["value"],
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
        const par = n.parentNode || '';
        if (typ === NODE_TYPE_CODE.START) {
            const keys = getNodeMapKeys(n.data?.wnode).sort().join(',');
            return `${n.id}:${typ}:${par}:${keys}`;
        }
        return `${n.id}:${typ}:${par}`;
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
 * Vue Flow 要求父节点在子节点之前出现在 nodes 数组中
 * @param {Array} nodes
 * @returns {Array}
 */
function sortVueFlowNodesParentFirst(nodes) {
    const byId = new Map(nodes.map(n => [n.id, n]));
    function depth(id) {
        let d = 0;
        let cur = byId.get(id);
        while (cur?.parentNode) {
            d++;
            cur = byId.get(cur.parentNode);
        }
        return d;
    }
    const roots = nodes.filter(n => !n.parentNode);
    const childs = nodes.filter(n => n.parentNode);
    childs.sort((a, b) => {
        const da = depth(a.id);
        const db = depth(b.id);
        if (da !== db) return da - db;
        return String(a.parentNode).localeCompare(String(b.parentNode));
    });
    return [...roots, ...childs];
}

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

    let vfNodes = wnodes.map(n => {
        const raw = {...n};
        const nodeStyle = raw.style;
        delete raw.style;
        delete raw.position;
        return {
            id: n.id,
            type: getVueFlowNodeType(n.type),
            position: n.position || {x: 0, y: 0},
            data: {wnode: raw},
            label: n.name || '',
            parentNode: n.parent || '',
            ...(nodeStyle && typeof nodeStyle === 'object' ? {style: nodeStyle} : {}),
        };
    });

    const vfEdges = wedges.map(e => ({
        id: `${e.from}-${e.to}-${generateUUID()}`,
        source: e.from,
        target: e.to,
        markerEnd: MarkerType.ArrowClosed,
        ...(e.fromHandle ? {sourceHandle: e.fromHandle} : {}),
    }));

    // 若需要自动布局，则计算位置（含嵌套子图）
    if (needLayout) {
        vfNodes = autoLayoutVueFlowNested(vfNodes, vfEdges, direction);
    }

    vfNodes = sortVueFlowNodesParentFirst(vfNodes);

    return {nodes: vfNodes, edges: vfEdges};
}

/**
 * 合并 Vue Flow 测量得到的 dimensions 到 style，便于与 NodeVO.style 一并写入 JSON
 * （避免仅有测量宽高、style 为空时无法保存大小）
 * @param {object} n - Vue Flow 节点
 * @returns {Record<string, string>|undefined}
 */
function mergeStyleWithDimensionsForSave(n) {
    const fromStyle = n.style && typeof n.style === 'object' ? {...n.style} : {};
    const d = n.dimensions;
    if (d && typeof d.width === 'number' && d.width > 0 && fromStyle.width == null) {
        fromStyle.width = `${Math.round(d.width)}px`;
    }
    if (d && typeof d.height === 'number' && d.height > 0 && fromStyle.height == null) {
        fromStyle.height = `${Math.round(d.height)}px`;
    }
    return Object.keys(fromStyle).length ? fromStyle : undefined;
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
        nodes: currentNodes.map(n => {
            const pos = n.position || {x: 0, y: 0};
            const style = mergeStyleWithDimensionsForSave(n);
            return {
                id: n.id,
                name: n.data?.wnode?.name || '',
                type: n.data?.wnode?.type || 0,
                configs: n.data?.wnode?.configs || [],
                position: {
                    x: typeof pos.x === 'number' ? pos.x : 0,
                    y: typeof pos.y === 'number' ? pos.y : 0,
                },
                parent: n.parentNode,
                ...(style ? {style} : {}),
            };
        }),
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
    const hasParent = (n) => !!(n.parent && String(n.parent).length);

    const rootStarts = nodes.filter(n => n.type === NODE_TYPE_CODE.START && !hasParent(n));
    const rootEnds = nodes.filter(n => n.type === NODE_TYPE_CODE.END && !hasParent(n));

    if (rootStarts.length === 0) {
        return fail('工作流必须包含一个起始节点', []);
    }
    if (rootStarts.length > 1) {
        return fail('工作流只能有一个起始节点', rootStarts.map(n => n.id));
    }
    if (rootEnds.length === 0) {
        return fail('工作流必须包含一个结束节点', []);
    }
    if (rootEnds.length > 1) {
        return fail('工作流只能有一个结束节点', rootEnds.map(n => n.id));
    }

    const startId = rootStarts[0].id;
    const endId = rootEnds[0].id;

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
        if (n.type === NODE_TYPE_CODE.START && hasParent(n)) continue;
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
        if (n.type === NODE_TYPE_CODE.END && hasParent(n)) continue;
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

    const nodeById = new Map(nodes.map(n => [n.id, n]));
    const badBreakPlacement = [];
    for (const n of nodes) {
        if (n.type !== NODE_TYPE_CODE.BREAK) continue;
        if (!hasParent(n)) {
            badBreakPlacement.push(n.id);
            continue;
        }
        const p = nodeById.get(String(n.parent));
        const pt = p?.type;
        if (!p || (pt !== NODE_TYPE_CODE.LOOP && pt !== NODE_TYPE_CODE.WHILE_LOOP)) {
            badBreakPlacement.push(n.id);
        }
    }
    if (badBreakPlacement.length) {
        const labels = badBreakPlacement.slice(0, 3).map((id) => {
            const node = nodes.find(x => x.id === id);
            return node?.name || id;
        }).join('、');
        const more = badBreakPlacement.length > 3 ? ` 等共 ${badBreakPlacement.length} 个` : '';
        return fail(
            `跳出节点只能作为循环或条件循环容器的子节点：${labels}${more}`,
            badBreakPlacement,
        );
    }

    const badBreakOut = [];
    for (const e of edges) {
        const from = e?.from;
        const to = e?.to;
        if (!from || !to) continue;
        const fromN = nodeById.get(from);
        const toN = nodeById.get(to);
        if (!fromN || !toN) continue;
        if (fromN.type !== NODE_TYPE_CODE.BREAK) continue;
        if (toN.type !== NODE_TYPE_CODE.END) {
            badBreakOut.push(from, to);
            continue;
        }
        const fp = hasParent(fromN) ? String(fromN.parent) : '';
        const tp = hasParent(toN) ? String(toN.parent) : '';
        if (fp !== tp) {
            badBreakOut.push(from, to);
        }
    }
    if (badBreakOut.length) {
        const uniq = [...new Set(badBreakOut)];
        return fail(
            '跳出节点的出边只能连接至同一循环容器内的结束节点',
            uniq,
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
 * 使用当前画布运行工作流（SSE），并在右侧栏展示日志
 */
async function handleRunWorkflow() {
    if (runningWorkflow.value) return;
    runAbortController?.abort();
    runAbortController = new AbortController();
    const signal = runAbortController.signal;

    clearNodeRunStates();
    runLogEntries.value = [];
    runDrawerOpen.value = true;
    if (!configDrawerVisible.value) {
        configPanelWidth.value = Math.max(configPanelWidth.value, 400);
    }

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
                        stateFlag: parsed.stateFlag,
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
 * 按 id 查找节点（统一为字符串比较，避免边与节点的 id 类型不一致导致查不到边）
 * @param {Array} nodes
 * @param {string} id
 */
function findNodeById(nodes, id) {
    if (id == null || id === '') return undefined;
    const s = String(id);
    return nodes.find(n => String(n.id) === s);
}

/**
 * 在同一 parent 子图内从 nodeId 反向 BFS，得到祖先 id（近→远，不含自身）
 * 使用 @vue-flow/core 的 getIncomers，与画布边连接判定一致（避免手写邻接表与 Vue Flow 的 id/嵌套处理不一致，导致 Loop 等容器上游池为空）
 */
function collectUpstreamNodeIdsScoped(nodeId, edges, nodes, scopeParent) {
    const sp = scopeParent == null || scopeParent === '' ? '' : String(scopeParent);
    const start = findNodeById(nodes, nodeId);
    if (!start) return [];
    const nid = String(nodeId);
    const norm = (n) => {
        const p = n.parentNode;
        if (p == null || p === '') return '';
        return String(p);
    };
    const inScope = n => norm(n) === sp;

    const order = [];
    const visited = new Set();
    const queue = [start];

    while (queue.length > 0) {
        const cur = queue.shift();
        const curId = String(cur.id);
        if (visited.has(curId)) continue;
        visited.add(curId);

        if (curId !== nid && inScope(cur)) {
            order.push(curId);
        }

        const incomers = getIncomers(cur, nodes, edges) || [];
        for (const inc of incomers) {
            if (!inc || !inScope(inc)) continue;
            const iid = String(inc.id);
            if (!visited.has(iid)) {
                queue.push(inc);
            }
        }
    }
    return order;
}

/**
 * 将单个节点的可引用变量追加到 pool
 */
async function appendNodeOutputsToPool(node, pool) {
    const upstreamId = node.id;
    const nodeCode = node.data?.wnode?.type;
    if (nodeCode == null) return;

    if (nodeCode === NODE_TYPE_CODE.START) {
        const variables = getNodeMapKeys(node.data?.wnode);
        variables.forEach((item) => {
            pool.push({
                name: `${upstreamId}:${item.name}`,
                des: item.des,
                type: item.type,
            });
        });
        return;
    }

    if (nodeCode === NODE_TYPE_CODE.VARIABLE_ASSIGN){
      // 如果节点为注册变量节点，应当直接将变量名注入变量池
      const variables = getNodeMapKeys(node.data?.wnode);
      let varName = "";
      let varType = "";
      let varDes = "";
      variables.forEach((item) => {
        if (item.name === "name") varName = item.value;
        if (item.name === "value") varType = item.type;
        if (item.name === "des") varDes = item.value;
      });
      if (!varName) return;
      if(pool.filter((item) => item.name === varName).length !== 0) return;
      pool.push({
        name: varName,
        des: varDes,
        type: varType
      });
      return;
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

/**
 * 沿 parentNode 链收集所有循环容器祖先 id（自内向外的顺序）
 * @param {string} nodeId
 * @param {Array} currentNodes
 * @returns {string[]}
 */
function collectLoopAncestorIds(nodeId, currentNodes) {
    const ids = [];
    let cur = findNodeById(currentNodes, nodeId);
    while (cur?.parentNode) {
        const pid = cur.parentNode;
        const parent = findNodeById(currentNodes, pid);
        if (parent?.data?.wnode?.type === NODE_TYPE_CODE.LOOP || parent?.data?.wnode?.type === NODE_TYPE_CODE.WHILE_LOOP) {
            ids.push(String(pid));
        }
        cur = parent;
    }
    return ids;
}

/**
 * 为处于循环子图内的节点追加 LoopNode.putLoopIIntoPool 写入的变量键：`{loopNodeId}:loop_i`
 * @param {string} nodeId
 * @param {Array<{name: string, des: string, type: string}>} pool
 * @param {Array} currentNodes
 */
function appendLoopIterationVarsToPool(nodeId, pool, currentNodes) {
    const existing = new Set(pool.map(p => p.name));
    for (const loopId of collectLoopAncestorIds(nodeId, currentNodes)) {
        const name = `${loopId}:loop_i`;
        if (existing.has(name)) continue;
        existing.add(name);
        pool.push({
            name,
            des: '当前循环下标（从 0 开始），与 LoopNode 运行时注入一致',
            type: 'Number',
        });
    }
}

/**
 * 构建指定节点的上游变量池：嵌套节点先继承可嵌套父节点（如循环）在主干上的上游池，再合并子图内上游
 * @param {string} nodeId - 目标节点 ID
 * @returns {Promise<Array<{name: string, des: string, type: string}>>}
 */
async function buildUpstreamPoolAsync(nodeId) {
    const currentEdges = getEdges.value;
    const currentNodes = getNodes.value;

    const node = findNodeById(currentNodes, nodeId);
    if (!node) return [];

    const parentId = node.parentNode || '';
    let inherited = [];
    if (parentId) {
        const pnode = findNodeById(currentNodes, parentId);
        const pCode = pnode?.data?.wnode?.type;
        if (pCode != null && (pCode & NESTABLE_FLAG) !== 0) {
            inherited = await buildUpstreamPoolAsync(String(parentId));
        }
    }

    const scopeKey = parentId || '';
    const innerOrder = collectUpstreamNodeIdsScoped(String(node.id), currentEdges, currentNodes, scopeKey);

    const pool = [...inherited];
    appendLoopIterationVarsToPool(nodeId, pool, currentNodes);
    for (const upstreamId of innerOrder) {
        const n = findNodeById(currentNodes, upstreamId);
        if (!n) continue;
        await appendNodeOutputsToPool(n, pool);
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
 * 若当前选中可嵌套容器（如循环），则在其内部添加子节点时返回该容器 id
 * @returns {string|null}
 */
function getNestableParentIdForAdd() {
    if (!selectedNodeId.value) return null;
    const n = findNode(selectedNodeId.value);
    if (!n) return null;
    const c = n.data?.wnode?.type;
    if (c != null && isNestableNodeType(c)) {
        return n.id;
    }
    return null;
}

/**
 * 从当前选中节点沿 parent 链查找最近的循环 / 条件循环容器 id（含选中自身）
 * @returns {string|null}
 */
function getEnclosingLoopParentIdForBreak() {
    if (!selectedNodeId.value) return null;
    let cur = findNode(selectedNodeId.value);
    while (cur) {
        const t = cur.data?.wnode?.type;
        if (t === NODE_TYPE_CODE.LOOP || t === NODE_TYPE_CODE.WHILE_LOOP) {
            return cur.id;
        }
        if (!cur.parentNode) return null;
        cur = findNode(cur.parentNode);
    }
    return null;
}

/**
 * 添加新节点
 * @param {Object} nodeType - 节点类型对象 { code, name, type }
 */
function addNode(nodeType) {
    const code = nodeType.code;
    let nestParentId = getNestableParentIdForAdd();

    const hasRootStart = getNodes.value.some(n =>
        n.data?.wnode?.type === NODE_TYPE_CODE.START && !n.parentNode);
    const hasRootEnd = getNodes.value.some(n =>
        n.data?.wnode?.type === NODE_TYPE_CODE.END && !n.parentNode);

    // 根图缺少起始/结束时优先补在根部，避免仅因选中循环而被当成「子图内添加」
    if (code === NODE_TYPE_CODE.START && !hasRootStart) {
        nestParentId = null;
    }
    if (code === NODE_TYPE_CODE.END && !hasRootEnd) {
        nestParentId = null;
    }

    if (isNestableNodeType(code)) {
        const loopId = generateUUID();
        const subStartId = generateUUID();
        const subEndId = generateUUID();
        const baseX = 100 + Math.random() * 200;
        const baseY = 100 + Math.random() * 200;
        addNodes([
            {
                id: loopId,
                type: 'loop',
                position: {x: baseX, y: baseY},
                style: {width: '560px', height: '340px'},
                data: {
                    wnode: {
                        id: loopId,
                        name: nodeType.name,
                        type: code,
                        configs: [],
                    },
                    outputs: [],
                },
                label: nodeType.name,
                zIndex: 0,
            },
            {
                id: subStartId,
                type: 'start',
                parentNode: loopId,
                position: {x: 40, y: 100},
                extent: 'parent',
                data: {
                    wnode: {
                        id: subStartId,
                        name: '开始',
                        type: NODE_TYPE_CODE.START,
                        configs: [],
                    },
                    outputs: [],
                },
                label: '开始',
                zIndex: 1,
            },
            {
                id: subEndId,
                type: 'end',
                parentNode: loopId,
                position: {x: 280, y: 100},
                extent: 'parent',
                data: {
                    wnode: {
                        id: subEndId,
                        name: '结束',
                        type: NODE_TYPE_CODE.END,
                        configs: [],
                    },
                    outputs: [],
                },
                label: '结束',
                zIndex: 1,
            },
        ]);
        addEdges([{
            id: `${subStartId}-${subEndId}-${generateUUID()}`,
            source: subStartId,
            target: subEndId,
            markerEnd: MarkerType.ArrowClosed,
        }]);
        return;
    }

    if (code === NODE_TYPE_CODE.START) {
        if (hasRootStart) {
            if (nestParentId) {
                const existing = getNodes.value.find(n =>
                    n.data?.wnode?.type === NODE_TYPE_CODE.START && n.parentNode === nestParentId);
                if (existing) {
                    message.warning('该循环内已有起始节点');
                    return;
                }
            } else {
                message.warning('每个工作流只允许拥有一个起始节点');
                return;
            }
        }
    }
    if (code === NODE_TYPE_CODE.END) {
        if (hasRootEnd) {
            if (nestParentId) {
                const existing = getNodes.value.find(n =>
                    n.data?.wnode?.type === NODE_TYPE_CODE.END && n.parentNode === nestParentId);
                if (existing) {
                    message.warning('该循环内已有结束节点');
                    return;
                }
            } else {
                message.warning('每个工作流只允许拥有一个结束节点');
                return;
            }
        }
    }

    if (code === NODE_TYPE_CODE.BREAK) {
        const loopPid = getEnclosingLoopParentIdForBreak();
        if (!loopPid) {
            message.warning('跳出节点仅允许添加在循环或条件循环容器内（请先选中容器内节点或循环框）');
            return;
        }
        nestParentId = loopPid;
    }

    const nodeId = generateUUID();
    let position = {
        x: 100 + Math.random() * 200,
        y: 100 + Math.random() * 200,
    };
    let parentNode;

    if (nestParentId) {
        parentNode = nestParentId;
        const siblings = getNodes.value.filter(n => n.parentNode === nestParentId);
        const idx = siblings.length;
        position = {x: 120 + idx * 36, y: 100};
    }

    const newNode = {
        id: nodeId,
        type: getVueFlowNodeType(code),
        position,
        ...(parentNode ? {parentNode} : {}),
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
        ...(parentNode ? {zIndex: 1} : {}),
    };

    addNodes([newNode]);
}

// ─── 嵌套拖拽：拖入 / 拖出可嵌套节点 ─────────────────────────────────────────

/**
 * 自根到当前节点的链（含自身）
 */
function getAncestorsChain(nodeId, nodeList) {
    const rev = [];
    let cur = nodeList.find(n => n.id === nodeId);
    while (cur) {
        rev.push(cur);
        if (!cur.parentNode) break;
        cur = nodeList.find(n => n.id === cur.parentNode);
    }
    return rev.reverse();
}

function getAbsolutePosition(nodeId, nodeList) {
    const chain = getAncestorsChain(nodeId, nodeList);
    let x = 0;
    let y = 0;
    chain.forEach(c => {
        x += c.position.x;
        y += c.position.y;
    });
    return {x, y};
}

function parsePixelSize(style) {
    const st = style || {};
    const w = parseFloat(String(st.width || '').replace(/px/g, ''));
    const h = parseFloat(String(st.height || '').replace(/px/g, ''));
    return {
        w: Number.isFinite(w) && w > 0 ? w : NODE_WIDTH,
        h: Number.isFinite(h) && h > 0 ? h : NODE_HEIGHT,
    };
}

function getNodePixelSize(node) {
    const code = node.data?.wnode?.type;
    if (code != null && isNestableNodeType(code)) {
        return parsePixelSize(node.style);
    }
    return {w: NODE_WIDTH, h: NODE_HEIGHT};
}

function pointInRect(px, py, rx, ry, rw, rh) {
    return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
}

/** 不能将 dragged 拖入自身或其后代节点内 */
function cannotNestDraggedInto(draggedId, candidateParentId, nodeList) {
    if (!candidateParentId) return false;
    if (candidateParentId === draggedId) return true;
    let cur = nodeList.find(n => n.id === candidateParentId);
    while (cur?.parentNode) {
        if (cur.parentNode === draggedId) return true;
        cur = nodeList.find(n => n.id === cur.parentNode);
    }
    return false;
}

/**
 * 返回包含 (cx,cy) 的最深层可嵌套容器 id（优先内层循环）
 */
function findDeepestContainingNestable(cx, cy, draggedId, nodeList) {
    const candidates = nodeList.filter(n => {
        const code = n.data?.wnode?.type;
        if (code == null || !isNestableNodeType(code)) return false;
        if (n.id === draggedId) return false;
        return !cannotNestDraggedInto(draggedId, n.id, nodeList);
    });

    candidates.sort((a, b) =>
        getAncestorsChain(b.id, nodeList).length - getAncestorsChain(a.id, nodeList).length);

    for (const c of candidates) {
        const abs = getAbsolutePosition(c.id, nodeList);
        const {w, h} = getNodePixelSize(c);
        if (pointInRect(cx, cy, abs.x, abs.y, w, h)) {
            return c.id;
        }
    }
    return null;
}

function isInnerFixedStartEnd(node) {
    const t = node.data?.wnode?.type;
    const p = node.parentNode;
    return !!(p && (t === NODE_TYPE_CODE.START || t === NODE_TYPE_CODE.END));
}

function isRootStartOrEnd(node) {
    const t = node.data?.wnode?.type;
    return !node.parentNode && (t === NODE_TYPE_CODE.START || t === NODE_TYPE_CODE.END);
}

function disconnectEdgesForNode(nodeId) {
    const toRemove = getEdges.value.filter(e => e.source === nodeId || e.target === nodeId);
    if (toRemove.length) {
        removeEdges(toRemove);
    }
}

/**
 * 拖放结束时：若进入或离开可嵌套区域则重挂 parentNode 并断开与该节点相连的旧边
 */
function onNodeDragStop({node: graphNode, nodes: draggedList}) {
    if (isLoading) return;
    const list = draggedList?.length ? draggedList : [graphNode];
    const nodeList = getNodes.value;
    const updates = new Map();

    for (const gn of list) {
        const n = nodeList.find(x => x.id === gn.id);
        if (!n) continue;
        if (isInnerFixedStartEnd(n)) continue;
        if (isRootStartOrEnd(n)) continue;

        const oldParent = n.parentNode || '';
        const abs = getAbsolutePosition(n.id, nodeList);
        const {w, h} = getNodePixelSize(n);
        const cx = abs.x + w / 2;
        const cy = abs.y + h / 2;

        const targetParent = findDeepestContainingNestable(cx, cy, n.id, nodeList) || '';
        if (targetParent === oldParent) continue;

        if (n.data?.wnode?.type === NODE_TYPE_CODE.BREAK && !targetParent && oldParent) {
            message.warning('跳出节点不能拖出循环容器外');
            continue;
        }

        disconnectEdgesForNode(n.id);

        let newPos;
        if (!targetParent) {
            newPos = {x: abs.x, y: abs.y};
        } else {
            const pAbs = getAbsolutePosition(targetParent, nodeList);
            newPos = {x: abs.x - pAbs.x, y: abs.y - pAbs.y};
        }

        updates.set(n.id, {
            parentNode: targetParent || undefined,
            position: newPos,
        });
    }

    if (updates.size === 0) return;

    setNodes(nds => nds.map(node => {
        const u = updates.get(node.id);
        return u ? {...node, ...u} : node;
    }));
    nextTick(() => syncConditionNextNodesFromEdges());
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

    const srcNode = findNode(params.source);
    const tgtNode = findNode(params.target);
    const st = srcNode?.data?.wnode?.type;
    const tt = tgtNode?.data?.wnode?.type;
    if (st === NODE_TYPE_CODE.BREAK) {
        if (tt !== NODE_TYPE_CODE.END) {
            message.warning('跳出节点的出边只能连接至结束节点');
            return;
        }
        const sp = srcNode.parentNode || '';
        const tp = tgtNode.parentNode || '';
        if (sp !== tp) {
            message.warning('跳出节点只能连接至同一循环容器内的结束节点');
            return;
        }
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
    const laid = autoLayoutVueFlowNested(currentNodes, currentEdges, direction);
    setNodes(sortVueFlowNodesParentFirst(laid));
    nextTick(() => fitView({padding: 0.2}));
}

// ─── 键盘快捷键 ───────────────────────────────────────────────────────────────

function onKeyDown(e) {
    // 忽略输入框中的按键
    const tag = document.activeElement?.tagName?.toLowerCase();
    if (tag === 'input' || tag === 'textarea') return;

    if (e.key === 'Delete' || e.key === 'Backspace') {
        let selNodes = getSelectedNodes.value || [];
        const protectedInner = selNodes.filter(n => {
            const t = n.data?.wnode?.type;
            const p = n.parentNode;
            return !!(p && (t === NODE_TYPE_CODE.START || t === NODE_TYPE_CODE.END));
        });
        if (protectedInner.length) {
            message.warning('循环内置起止节点不可删除');
        }
        selNodes = selNodes.filter(n => !protectedInner.some(p => p.id === n.id));

        const extras = [];
        for (const n of selNodes) {
            if (n.data?.wnode?.type === NODE_TYPE_CODE.LOOP) {
                getNodes.value
                    .filter(x => x.parentNode === n.id)
                    .forEach(c => extras.push(c));
            }
        }

        const seen = new Set();
        const toRemove = [];
        for (const n of [...selNodes, ...extras]) {
            if (seen.has(n.id)) continue;
            seen.add(n.id);
            toRemove.push(n);
        }
        if (toRemove.length > 0) {
            removeNodes(toRemove);
        }

        const selEdges = getSelectedEdges.value;
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

    <!-- 主工作区：画布与右侧栏同级，不遮挡顶部工具栏 -->
    <div class="workspace">
      <div class="flow-canvas">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :delete-key-code="null"
          :connect-on-click="false"
          :elevate-edges-on-select="true"
          :default-edge-options="defaultEdgeOpts"
          fit-view-on-init
          @node-click="onNodeClick"
          @node-drag-stop="onNodeDragStop"
          @nodes-change="onNodesChange"
          @edges-change="onEdgesChange"
        >
          <Controls />
          <MiniMap />
        </VueFlow>
      </div>

      <aside
        v-if="showSideRail"
        class="side-rail"
        :style="{ width: configPanelWidth + 'px' }"
      >
        <div
          class="resize-handle"
          @mousedown="onResizeHandleMouseDown"
        />

        <div
          v-if="configDrawerVisible"
          class="side-section side-section--config"
        >
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

        <div
          v-if="runDrawerOpen"
          class="side-section side-section--run"
          :class="{ 'side-section--run-bordered': configDrawerVisible }"
        >
          <div class="run-log-header">
            <span class="run-log-title">运行日志</span>
            <Button
              type="text"
              size="small"
              @click="runDrawerOpen = false"
            >
              ✕
            </Button>
          </div>
          <div class="run-log-options">
            <div class="run-log-option-item">
              <label for="hideState">隐藏状态日志</label>
              <Tooltip title="隐藏状态日志">
                <Switch v-model:checked="hideStateFlag" id="hideState"></Switch>
              </Tooltip>
            </div>
          </div>
          <div ref="runLogBodyRef" class="run-log-body">
            <div
              v-for="(entry, idx) in runLogEntries.filter(item=> !hideStateFlag || !item.stateFlag)"
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
        </div>
      </aside>
    </div>
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

.workspace {
  flex: 1;
  display: flex;
  flex-direction: row;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.flow-canvas {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

/* 确保 Vue-Flow 填满容器 */
:deep(.vue-flow) {
  width: 100%;
  height: 100%;
}

/* 边线在节点层之上，便于子图内选中边 */
.flow-canvas :deep(.vue-flow__edges) {
  z-index: 2;
}

.flow-canvas :deep(.vue-flow__nodes) {
  z-index: 1;
}

.flow-canvas :deep(.vue-flow__resize-control) {
  z-index: 3;
}

/* ── 与画布同级的右侧栏 ── */
.side-rail {
  position: relative;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  border-left: 1px solid #f0f0f0;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.06);
  min-width: 280px;
  max-width: 800px;
}

.side-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.side-section--run-bordered {
  border-top: 1px solid #f0f0f0;
}

.run-log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.run-log-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
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
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  font-size: 13px;
  padding: 0 16px 12px;
}

.run-log-options{
  padding: 12px 16px 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.run-log-option-item{
  position: relative;
  display: flex;
  align-items: center;
  justify-content: left;
}

.run-log-option-item label{
  margin-right: 5px;
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
