/**
 * 工作流自动布局工具
 * 使用 dagre 对有向图分层布局，支持按节点实际宽高占位（适合可变大小节点如循环容器）
 */

import dagre from 'dagre';

export const NODE_WIDTH = 160;
export const NODE_HEIGHT = 60;
const H_GAP = 80;
const V_GAP = 60;

/**
 * 从 Vue-Flow 节点或布局输入对象解析宽高
 * @param {object} n - 含 id，可选 style / width / height
 * @returns {{ width: number, height: number }}
 */
export function getLayoutDimensionsForNode(n) {
    if (!n) {
        return {width: NODE_WIDTH, height: NODE_HEIGHT};
    }
    if (typeof n.width === 'number' && typeof n.height === 'number' && n.width > 0 && n.height > 0) {
        return {width: n.width, height: n.height};
    }
    const st = n.style || {};
    const w = parseFloat(String(st.width || '').replace(/px/g, ''));
    const h = parseFloat(String(st.height || '').replace(/px/g, ''));
    return {
        width: Number.isFinite(w) && w > 0 ? w : NODE_WIDTH,
        height: Number.isFinite(h) && h > 0 ? h : NODE_HEIGHT,
    };
}

/**
 * 使用 dagre 计算节点坐标（position 为左上角，与 Vue Flow 一致）
 * @param {Array<{ id: string, width?: number, height?: number, style?: object }>} nodes
 * @param {Array<{ from: string, to: string }>} edges
 * @param {'horizontal'|'vertical'} direction
 * @returns {Array} 带 position 的节点数组
 */
export function autoLayout(nodes, edges, direction = 'horizontal') {
    if (!nodes || nodes.length === 0) return nodes;

    const withDims = nodes.map(n => ({
        ...n,
        ...getLayoutDimensionsForNode(n),
    }));

    if (withDims.length === 1) {
        return [{
            ...withDims[0],
            position: {x: 0, y: 0},
        }];
    }

    const g = new dagre.graphlib.Graph({multigraph: false});
    const rankdir = direction === 'horizontal' ? 'LR' : 'TB';
    g.setGraph({
        rankdir,
        // LR: ranksep 为沿箭头方向层间距；nodesep 为同层垂直方向节点间距
        // TB: ranksep 为垂直层间距；nodesep 为同层水平间距
        nodesep: direction === 'horizontal' ? V_GAP : H_GAP,
        ranksep: direction === 'horizontal' ? H_GAP : V_GAP,
        marginx: 48,
        marginy: 48,
    });
    g.setDefaultEdgeLabel(() => ({}));

    withDims.forEach(n => {
        g.setNode(n.id, {width: n.width, height: n.height});
    });

    const nodeIds = new Set(withDims.map(n => n.id));
    (edges || []).forEach(e => {
        if (nodeIds.has(e.from) && nodeIds.has(e.to) && e.from !== e.to) {
            g.setEdge(e.from, e.to);
        }
    });

    try {
        dagre.layout(g);
    } catch (e) {
        console.warn('[layout] dagre.layout 失败，使用线性回退', e);
        return withDims.map((n, i) => ({
            ...n,
            position: direction === 'horizontal'
                ? {x: i * (NODE_WIDTH + H_GAP), y: 0}
                : {x: 0, y: i * (NODE_HEIGHT + V_GAP)},
        }));
    }

    return withDims.map(n => {
        const ln = g.node(n.id);
        if (!ln) {
            return {...n, position: {x: 0, y: 0}};
        }
        const {x, y, width, height} = ln;
        return {
            ...n,
            position: {
                x: x - width / 2,
                y: y - height / 2,
            },
        };
    });
}

/**
 * 对 Vue-Flow 格式的节点进行自动布局
 * @param {Array} vfNodes - Vue-Flow Node 数组（含 id、可选 style）
 * @param {Array} vfEdges - Vue-Flow Edge 数组（含 source、target）
 * @param {'horizontal'|'vertical'} direction - 布局方向
 * @returns {Array} 带有更新后 position 的 Vue-Flow 节点数组
 */
export function autoLayoutVueFlow(vfNodes, vfEdges, direction = 'horizontal') {
    const nodes = vfNodes.map(n => ({
        id: n.id,
        style: n.style,
        width: n.width,
        height: n.height,
    }));
    const edges = vfEdges.map(e => ({from: e.source, to: e.target}));

    const laid = autoLayout(nodes, edges, direction);
    const posMap = {};
    laid.forEach(n => {
        posMap[n.id] = n.position;
    });

    return vfNodes.map(n => ({
        ...n,
        position: posMap[n.id] || n.position || {x: 0, y: 0},
    }));
}

const NESTED_PAD = 32;
const NESTED_HEADER = 36;

/**
 * 根级节点自动布局 + 各父容器内子图单独布局（相对坐标），并调整容器宽高。
 * @param {Array} vfNodes - Vue-Flow 节点
 * @param {Array} vfEdges - Vue-Flow 边
 * @param {'horizontal'|'vertical'} direction
 * @returns {Array}
 */
export function autoLayoutVueFlowNested(vfNodes, vfEdges, direction = 'horizontal') {
    const hasNested = vfNodes.some(n => n.parentNode);
    if (!hasNested) {
        return autoLayoutVueFlow(vfNodes, vfEdges, direction);
    }

    const byId = new Map(vfNodes.map(n => [n.id, n]));
    const roots = vfNodes.filter(n => !n.parentNode);
    const rootIds = new Set(roots.map(n => n.id));
    const rootEdges = vfEdges.filter(e => rootIds.has(e.source) && rootIds.has(e.target));
    const laidRoots = autoLayoutVueFlow(roots, rootEdges, direction);

    const childrenByParent = new Map();
    vfNodes.forEach(n => {
        if (!n.parentNode) return;
        if (!childrenByParent.has(n.parentNode)) childrenByParent.set(n.parentNode, []);
        childrenByParent.get(n.parentNode).push(n);
    });

    function parentDepth(pid) {
        let d = 0;
        let cur = byId.get(pid);
        while (cur?.parentNode) {
            d++;
            cur = byId.get(cur.parentNode);
        }
        return d;
    }

    const parentIdsSorted = [...childrenByParent.keys()].sort((a, b) => parentDepth(a) - parentDepth(b));

    const resultMap = new Map();
    laidRoots.forEach(n => {
        const orig = byId.get(n.id);
        resultMap.set(n.id, {...orig, position: n.position});
    });

    for (const parentId of parentIdsSorted) {
        const children = childrenByParent.get(parentId);
        if (!children.length) continue;
        const parent = resultMap.get(parentId);
        if (!parent) continue;

        const childIds = new Set(children.map(c => c.id));
        const childEdges = vfEdges.filter(e => childIds.has(e.source) && childIds.has(e.target));
        const innerNodes = children.map(c => ({
            id: c.id,
            style: c.style,
            width: c.width,
            height: c.height,
        }));
        const laidInner = autoLayout(innerNodes, childEdges, direction);

        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;
        laidInner.forEach(n => {
            const p = n.position;
            const {width, height} = getLayoutDimensionsForNode(n);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x + width);
            maxY = Math.max(maxY, p.y + height);
        });
        if (!isFinite(minX)) {
            minX = 0;
            minY = 0;
            maxX = NODE_WIDTH;
            maxY = NODE_HEIGHT;
        }

        const offX = NESTED_PAD - minX;
        const offY = NESTED_PAD + NESTED_HEADER - minY;
        const W = Math.max(maxX - minX + 2 * NESTED_PAD, 220);
        const H = Math.max(maxY - minY + 2 * NESTED_PAD + NESTED_HEADER, 160);

        resultMap.set(parentId, {
            ...parent,
            style: {
                ...(parent.style || {}),
                width: `${W}px`,
                height: `${H}px`,
            },
        });

        laidInner.forEach(ln => {
            const orig = byId.get(ln.id);
            resultMap.set(ln.id, {
                ...orig,
                position: {
                    x: ln.position.x + offX,
                    y: ln.position.y + offY,
                },
                extent: orig.extent,
            });
        });
    }

    return vfNodes.map(n => resultMap.get(n.id) || n);
}
