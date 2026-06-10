<script setup>
import {Handle, Position} from '@vue-flow/core';
import {computed, ref, inject} from 'vue';

const props = defineProps(['id', 'data']);

const layoutDirection = inject('layoutDirection', 'horizontal');

const targetPosition = computed(() =>
  layoutDirection.value === 'vertical' ? Position.Top : Position.Left
);
const sourcePosition = computed(() =>
  layoutDirection.value === 'vertical' ? Position.Bottom : Position.Right
);

const noCondition = ref(true);

/**
 * 每条 type===Condition 的配置对应一行 UI 与一个 source-{index} 句柄（与后端多条 Condition 配置一致）
 */
const conditionRows = computed(() => {
  const wnode = props.data?.wnode;
  if (!wnode?.configs) {
    return [{key: 'placeholder', shortLabel: '默认', fullLabel: '默认分支', isPlaceholder: true}];
  }

  const list = wnode.configs.filter(c => c.type === 'Condition');
  if (list.length === 0) {
    noCondition.value = true;
    return [{key: 'placeholder', shortLabel: '（请添加条件配置）', fullLabel: '', isPlaceholder: true}];
  }
  noCondition.value = false;

  return list.map((cfg, index) => {
    try {
      const parsed = JSON.parse(cfg.value || '{}');
      const cond = Array.isArray(parsed) ? (parsed[0] || {}) : parsed;
      const a = String(cond.a ?? '');
      const b = String(cond.b ?? '');
      const op = cond.operator || '==';
      const full = `${a} ${op} ${b}`.trim();
      const short = full.length > 48 ? `${full.slice(0, 45)}…` : full;
      const nameTag = cfg.name ? `${cfg.name} · ` : '';
      return {
        key: cfg.name || `cond-${index}`,
        index,
        shortLabel: `${short || `分支 ${index + 1}`}`,
        fullLabel: full || `${cfg.name || '条件'} ${index + 1}`,
        isPlaceholder: false,
      };
    } catch {
      return {
        key: `err-${index}`,
        index,
        shortLabel: '配置解析失败',
        fullLabel: '',
        isPlaceholder: true,
      };
    }
  });
});

const uiStateClass = computed(() => {
  if (props.data?.precheckError) return 'state-precheck-error';
  const state = props.data?.runState;
  if (state === 2) return 'state-running';
  if (state === 256) return 'state-done';
  if (state === 1) return 'state-error';
  if (state === 512) return 'state-disabled';
  return '';
});
</script>

<template>
  <div class="workflow-node condition-node" :class="uiStateClass">
    <Handle type="target" :position="targetPosition" id="target" />

    <div class="condition-body">
      <div class="node-title">{{ data?.wnode?.name || '条件节点' }}</div>

      <div
        v-for="(row, index) in conditionRows"
        :key="row.key"
        class="condition-row"
      >
        <span class="condition-expr" :title="row.fullLabel">{{ row.shortLabel }}</span>
        <Handle
          v-if="!noCondition"
          type="source"
          :position="sourcePosition"
          :id="`source-${index}`"
          class="condition-source-handle"
          :class="layoutDirection === 'vertical' ? 'condition-source-handle--bottom' : 'condition-source-handle--right'"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.workflow-node.condition-node {
  min-width: 200px;
  max-width: 150px;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 500;
  border: 2px solid transparent;
  cursor: pointer;
  user-select: none;
  position: relative;
  overflow: visible;
  transition: box-shadow 0.2s, border-color 0.2s;
  background-color: #ffffff;
  color: #262626;
  border-color: #1677ff;
  box-shadow: 0 1px 4px rgba(22, 119, 255, 0.15);
}

.condition-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 4px;
  pointer-events: none;
}

.condition-row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 28px;
  padding: 4px 22px 4px 8px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  gap: 8px;
}

.condition-expr {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: 11px;
  font-weight: 400;
  color: #595959;
  line-height: 1.35;
  word-break: break-all;
  pointer-events: none;
  max-width: 100%;
  text-overflow: ellipsis;
  overflow: hidden;
  text-wrap: nowrap;
}

.condition-source-handle {
  position: absolute !important;
  z-index: 999;
}

.condition-source-handle--right {
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
}

.condition-source-handle--bottom {
  bottom: -12px;
  left: 50%;
  transform: translateX(-50%);
}

.state-running {
  border-color: #1677ff !important;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.3) !important;
}

.state-done {
  border-color: #52c41a !important;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.3) !important;
}

.state-error {
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.3) !important;
}

.state-precheck-error {
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.45) !important;
}

.state-disabled {
  opacity: 0.4;
}
</style>
