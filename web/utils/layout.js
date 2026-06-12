/**
 * 工作流自动布局工具
 * 使用 dagre 对有向图分层布局，支持按节点实际宽高占位（适合可变大小节点如循环容器）
 */

import dagre from 'dagre';

export const NODE_WIDTH = 160;
export const NODE_HEIGHT = 60;
const H_GAP = 80;
const V_GAP = 60;

export function autoLayoutRef(vfNodes, vfEdges, direction = 'horizontal'){
    const parentIdMap = new Map();
    vfNodes.filter(item => item.parentNode).map(item => parentIdMap.set(item.parentNode, true));
    const nestableNodes = [""];
    parentIdMap.keys().forEach(item => nestableNodes.push(item));
    const byId = new Map(vfNodes.map(n => [n.id, n]));
    function getParentDepth(pid){
        let d = 1;
        let cur = byId.get(pid);
        while (cur?.parentNode) {
            d++;
            cur = byId.get(cur.parentNode);
        }
        return d;
    }

    const pidSort = nestableNodes.sort((a, b) => getParentDepth(b) - getParentDepth(a));
    const rankdir = direction === 'horizontal' ? 'LR' : 'TB';
    for(let pid of pidSort){
        const nodes = vfNodes.filter(item => item.parentNode === pid);
        if (nodes.length === 0) continue;
        const edges = vfEdges.filter(item => {
            return item.sourceNode.parentNode === pid || item.targetNode.parentNode === pid;
        })
        let g = new dagre.graphlib.Graph();
        g.setGraph({
            rankdir,
            nodesep: direction === 'horizontal' ? V_GAP : H_GAP,
            ranksep: direction === 'horizontal' ? H_GAP : V_GAP,
            marginx: 48,
            marginy: 48,
        });
        g.setDefaultEdgeLabel(function() { return {}; });
        nodes.forEach(item => {
            g.setNode(item.id, {
                label: item.id,
                width: item.dimensions.width,
                height: item.dimensions.height
            });
        });
        edges.forEach(item => {
            g.setEdge(item.sourceNode.id, item.targetNode.id);
        });
        dagre.layout(g);

        let minX = 0, minY = 0, maxX = 0, maxY = 0;
        const offsetY = 20;
        nodes.forEach(item => {
            const ln = g.node(item.id);
            const {x, y, height, width} = ln;
            const rx = x - width / 2;
            const ry = y - height / 2 + offsetY;
            item.position = {
                x: rx,
                y: ry
            };
            minX = Math.min(rx, minX);
            minY = Math.min(ry, minY);
            maxX = Math.max(rx + width, maxX);
            maxY = Math.max(ry + height, maxY);
        })
        const parentNode = byId.get(pid);
        if (!parentNode) continue;
        const pWidth = Math.max(maxX - minX + 20, 280);
        const pHeight = Math.max(maxY - minY + 20, 200);
        parentNode.dimensions = {
            ...parentNode.dimensions,
            width: pWidth,
            height: pHeight
        }
        const subOffsetX = (pWidth - maxX + minX - 40) / 2;
        const subOffsetY = (pHeight - maxY + minY - 40) / 2;
        nodes.forEach(item => {
            item.position = {
                x: item.position.x + subOffsetX,
                y: item.position.y + subOffsetY
            }
        })
    }
    vfNodes.forEach(item => {
        item.style = {
            ...item.style,
            width: `${item.dimensions.width}px`,
            height: `${item.dimensions.height}px`
        }
    });
    return vfNodes;
}
