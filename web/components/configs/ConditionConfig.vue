<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { Form, Select, Textarea, AutoComplete, Tooltip, Row, Col } from 'ant-design-vue';

const props = defineProps({
  config: {
    type: Object,
    required: true
  },
  modelValue: {
    type: String,
    default: ''
  },
  pool: {
    type: Array,
    default: () => []
  },
  requestPoolRefresh: {
    type: Function,
    default: null
  }
});

const emit = defineEmits(['update:modelValue']);

const operatorOptions = [
  { value: '==', label: '等于 (==)' },
  { value: '!=', label: '不等于 (!=)' },
  { value: '<', label: '小于 (<)' },
  { value: '>', label: '大于 (>)' },
  { value: '<=', label: '小于等于 (<=)' },
  { value: '>=', label: '大于等于 (>=)' }
];

/** 单条条件（与后端 ConditionConfig 一致，含 nextNodes 由画布同步） */
const row = ref({
  a: '',
  operator: '==',
  b: '',
  nextNodes: [],
  cacheBefore: '',
  cacheAfter: '',
  cacheBeforeRight: '',
  cacheAfterRight: '',
  leftVisible: false,
  rightVisible: false
});

let leftTaRef = null;
let rightTaRef = null;
/** AutoComplete 内 Textarea 的 keydown 往往不冒泡到外层，改为挂原生 textarea */
let leftKeydownCleanup = null;
let rightKeydownCleanup = null;

function bindLeftTa(el) {
  leftTaRef = el;
  leftKeydownCleanup?.();
  leftKeydownCleanup = null;
  if (!el) return;
  const attach = (ta) => {
    const handler = (e) => {
      onLeftKeydown(e);
    };
    ta.addEventListener('keydown', handler);
    leftKeydownCleanup = () => {
      ta.removeEventListener('keydown', handler);
    };
  };
  nextTick(() => {
    let ta = getTextareaEl(el);
    if (!ta) {
      requestAnimationFrame(() => {
        ta = getTextareaEl(el);
        if (ta) attach(ta);
      });
      return;
    }
    attach(ta);
  });
}

function bindRightTa(el) {
  rightTaRef = el;
  rightKeydownCleanup?.();
  rightKeydownCleanup = null;
  if (!el) return;
  const attach = (ta) => {
    const handler = (e) => {
      onRightKeydown(e);
    };
    ta.addEventListener('keydown', handler);
    rightKeydownCleanup = () => {
      ta.removeEventListener('keydown', handler);
    };
  };
  nextTick(() => {
    let ta = getTextareaEl(el);
    if (!ta) {
      requestAnimationFrame(() => {
        ta = getTextareaEl(el);
        if (ta) attach(ta);
      });
      return;
    }
    attach(ta);
  });
}

function getTextareaEl(root) {
  if (!root) return null;
  return root.resizableTextArea?.textArea || root.$el?.querySelector?.('textarea') || null;
}

const filteredOptions = computed(() => {
  if (!props.pool || props.pool.length === 0) return [];
  return props.pool.map(item => ({
    value: `{{${item.name}}}`,
    name: item.name,
    desc: item.des || item.type || ''
  }));
});

function parseOneCondition(raw) {
  const empty = { a: '', operator: '==', b: '', nextNodes: [] };
  if (!raw || typeof raw !== 'string') return { ...empty };
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      const first = parsed[0];
      if (first && typeof first === 'object') {
        return {
          a: String(first.a ?? ''),
          operator: first.operator || '==',
          b: String(first.b ?? ''),
          nextNodes: Array.isArray(first.nextNodes) ? first.nextNodes : []
        };
      }
      return { ...empty };
    }
    if (parsed && typeof parsed === 'object') {
      return {
        a: String(parsed.a ?? ''),
        operator: parsed.operator || '==',
        b: String(parsed.b ?? ''),
        nextNodes: Array.isArray(parsed.nextNodes) ? parsed.nextNodes : []
      };
    }
  } catch {
    /* ignore */
  }
  return { ...empty };
}

function initFromModelValue() {
  const p = parseOneCondition(props.modelValue);
  row.value = {
    ...p,
    cacheBefore: '',
    cacheAfter: '',
    cacheBeforeRight: '',
    cacheAfterRight: '',
    leftVisible: false,
    rightVisible: false
  };
}

function syncToModelValue() {
  const r = row.value;
  const payload = {
    a: r.a,
    operator: r.operator,
    b: r.b,
    nextNodes: Array.isArray(r.nextNodes) ? r.nextNodes : []
  };
  emit('update:modelValue', JSON.stringify(payload));
}

onMounted(() => {
  initFromModelValue();
});

onBeforeUnmount(() => {
  leftKeydownCleanup?.();
  rightKeydownCleanup?.();
  leftKeydownCleanup = null;
  rightKeydownCleanup = null;
});

/** 切换不同配置行时整表重载；避免 watch modelValue 以免输入时被父级回传打断 */
watch(
  () => props.config?._rowKey,
  () => {
    initFromModelValue();
  }
);

/** 画布同步仅更新 nextNodes 时合并，不重载左右值 */
watch(
  () => props.modelValue,
  (nv) => {
    try {
      const parsed = JSON.parse(nv || '{}');
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return;
      if (Array.isArray(parsed.nextNodes)) {
        row.value.nextNodes = parsed.nextNodes;
      }
    } catch {
      /* ignore */
    }
  }
);

async function onLeftKeydown(e) {
  if (e.key !== '/') return;
  if (props.requestPoolRefresh) {
    try {
      await props.requestPoolRefresh();
    } catch {
      /* ignore */
    }
  }
  await nextTick();
  if (filteredOptions.value.length === 0) return;
  nextTick(() => {
    const ta = getTextareaEl(leftTaRef);
    if (!ta) return;
    const val = ta.value;
    const pos = ta.selectionStart;
    if (pos >= 1 && val.charAt(pos - 1) === '/') {
      row.value.cacheBefore = val.slice(0, pos - 1);
      row.value.cacheAfter = val.slice(pos);
      row.value.leftVisible = true;
    }
  });
}

function onLeftSelect(value) {
  row.value.a = row.value.cacheBefore + value + row.value.cacheAfter;
  row.value.cacheBefore = '';
  row.value.cacheAfter = '';
  row.value.leftVisible = false;
  syncToModelValue();
}

async function onRightKeydown(e) {
  if (e.key !== '/') return;
  if (props.requestPoolRefresh) {
    try {
      await props.requestPoolRefresh();
    } catch {
      /* ignore */
    }
  }
  await nextTick();
  if (filteredOptions.value.length === 0) return;
  nextTick(() => {
    const ta = getTextareaEl(rightTaRef);
    if (!ta) return;
    const val = ta.value;
    const pos = ta.selectionStart;
    if (pos >= 1 && val.charAt(pos - 1) === '/') {
      row.value.cacheBeforeRight = val.slice(0, pos - 1);
      row.value.cacheAfterRight = val.slice(pos);
      row.value.rightVisible = true;
    }
  });
}

function onRightSelect(value) {
  row.value.b = row.value.cacheBeforeRight + value + row.value.cacheAfterRight;
  row.value.cacheBeforeRight = '';
  row.value.cacheAfterRight = '';
  row.value.rightVisible = false;
  syncToModelValue();
}

function onOperatorChange(value) {
  row.value.operator = value;
  syncToModelValue();
}

function onLeftValueUpdate(v) {
  row.value.a = v;
  syncToModelValue();
}

function onRightValueUpdate(v) {
  row.value.b = v;
  syncToModelValue();
}
</script>

<template>
  <div class="condition-config">
    <p class="condition-hint"></p>
    <Form layout="vertical">
      <Row :gutter="8" align="middle">
        <Col :span="8">
          <Form.Item label="左值">
            <Tooltip title="输入值或通过`/`输入上游节点变量，值默认为字符串">
              <AutoComplete
                :value="row.a"
                class="left-input"
                :options="filteredOptions"
                :open="row.leftVisible"
                :filter-option="false"
                placeholder="输入值或通过`/`输入上游节点变量，值默认为字符串"
                style="width: 100%"
                @select="onLeftSelect"
                @update:value="onLeftValueUpdate"
                @dropdown-visible-change="(open) => { if (!open) row.leftVisible = false }"
              >
                <Textarea
                  :ref="bindLeftTa"
                  :rows="1"
                />
              </AutoComplete>
            </Tooltip>
          </Form.Item>
        </Col>
        <Col :span="8">
          <Form.Item label="运算符">
            <Select
              v-model:value="row.operator"
              :options="operatorOptions"
              style="width: 100%"
              @change="onOperatorChange"
            />
          </Form.Item>
        </Col>
        <Col :span="8">
          <Form.Item label="右值">
            <Tooltip title="输入值或通过`/`输入上游节点变量，值默认为字符串">
              <AutoComplete
                :value="row.b"
                class="right-input"
                :options="filteredOptions"
                :open="row.rightVisible"
                :filter-option="false"
                placeholder="输入值或通过`/`输入上游节点变量，值默认为字符串"
                style="width: 100%"
                @select="onRightSelect"
                @update:value="onRightValueUpdate"
                @dropdown-visible-change="(open) => { if (!open) row.rightVisible = false }"
              >
                <Textarea
                  :ref="bindRightTa"
                  :rows="1"
                />
              </AutoComplete>
            </Tooltip>
          </Form.Item>
        </Col>
      </Row>
    </Form>
  </div>
</template>

<style scoped>
.condition-config {
  width: 100%;
}

.condition-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.5;
}

:deep(.ant-form-item) {
  margin-bottom: 0;
}

:deep(.ant-form-item-label) {
  padding-bottom: 4px;
}

:deep(.ant-form-item-label > label) {
  font-size: 12px;
  color: #999;
}
</style>
