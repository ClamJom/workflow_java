<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { Spin, Empty, Alert, Button, message, Select, Input, Space } from 'ant-design-vue';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue';
import { getConfigComponent } from './configs/index.js';
import api from '../api/index.js';
import { generateUUID } from '../utils/token.js';

const props = defineProps({
  nodeCode: {
    type: Number,
    required: true
  },
  initialConfigs: {
    type: Array,
    default: () => []
  },
  pool: {
    type: Array,
    default: () => []
  },
  /** 按当前画布重新计算上游变量池（输入 `/` 时调用，保证实时） */
  requestPoolRefresh: {
    type: Function,
    default: null
  },
  nodeName: {
    type: String,
    default: ''
  },
  nodeId: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['configs-change']);

const configs = ref([]);
const configValues = reactive({});
const loading = ref(false);
const errorMsg = ref('');

/** 条件节点 code，与 NodeType.CONDITION 一致 */
const CONDITION_NODE_CODE = 0x000004;

/** 单条 Condition 配置 value，与后端 ConditionConfig JSON 一致（非数组） */
const DEFAULT_CONDITION_JSON = JSON.stringify({
  operator: '==',
  a: '',
  b: '',
  nextNodes: [],
});

/** 非条件节点可选的配置类型（与 ConfigTypes.java 一致） */
const CONFIG_TYPE_OPTIONS = [
  { value: 'String', label: '字符串 String' },
  { value: 'Number', label: '数字 Number' },
  { value: 'Boolean', label: '布尔 Boolean' },
  { value: 'List', label: '列表 List' },
  { value: 'Map', label: '键值对 Map' },
  { value: 'Condition', label: '条件 Condition' },
  { value: 'Select', label: '下拉 Select' },
  { value: 'Slider', label: '滑块 Slider' }
];

const pendingType = ref('String');
const pendingName = ref('');
const pendingDes = ref('');

const typeOptionsForNode = computed(() => {
  if (props.nodeCode === CONDITION_NODE_CODE) {
    return CONFIG_TYPE_OPTIONS.filter(o => o.value === 'Condition');
  }
  return CONFIG_TYPE_OPTIONS;
});

function defaultValueForType(type) {
  switch (type) {
    case 'Boolean':
      return 'false';
    case 'Number':
    case 'Slider':
      return '0';
    case 'List':
      return '[]';
    case 'Map':
      return '{}';
    case 'Condition':
      return DEFAULT_CONDITION_JSON;
    default:
      return '';
  }
}

function mapInitialToConfigs(initial) {
  return initial.map((c, idx) => ({
    id: typeof c.id === 'number' ? c.id : idx + 1,
    name: c.name,
    des: c.des ?? c.name ?? '',
    type: c.type,
    value: c.value ?? '',
    options: c.options,
    min: c.min,
    max: c.max,
    k: c.k ?? 1,
    quantize: c.quantize ?? 0,
    required: c.required ?? false,
    parent: c.parent ?? 0,
    _rowKey: generateUUID()
  }));
}

function normalizeConfigRows(list) {
  return (list || []).map((c, idx) => ({
    ...c,
    _rowKey: c._rowKey || generateUUID(),
    id: c.id != null ? c.id : idx + 1
  }));
}

function resetPendingForm() {
  if (props.nodeCode === CONDITION_NODE_CODE) {
    pendingType.value = 'Condition';
    pendingName.value = '';
    pendingDes.value = '条件';
  } else {
    pendingType.value = 'String';
    pendingName.value = '';
    pendingDes.value = '';
  }
}

function isConfigVisible(config) {
  if (!config.parent || config.parent === 0) return true;
  const parentConfig = configs.value.find(c => c.id === config.parent) ||
    configs.value.find(c => c.name === config.parent);
  if (!parentConfig) return true;
  if (parentConfig.type === 'Boolean') {
    return configValues[parentConfig.name] === 'true';
  }
  return true;
}

function needsPool(type) {
  return type === 'String' || type === 'Number' || type === 'Condition' || type === 'Map' || type === 'List';
}

function poolPropsFor(type) {
  if (!needsPool(type)) return {};
  return {
    pool: props.pool,
    requestPoolRefresh: props.requestPoolRefresh,
  };
}

function onConfigValueChange(configName, newValue) {
  configValues[configName] = newValue;
}

function emitConfigsChange() {
  const result = configs.value.map((c) => {
    const { _rowKey, ...rest } = c;
    return {
      ...rest,
      value: configValues[rest.name] !== undefined ? configValues[rest.name] : rest.value
    };
  });
  emit('configs-change', result);
}

function saveConfigs() {
  emitConfigsChange();
  message.success('配置已保存到节点');
}

function initConfigValues() {
  const names = new Set(configs.value.map(c => c.name));
  Object.keys(configValues).forEach((k) => {
    if (!names.has(k)) delete configValues[k];
  });

  const initialMap = {};
  if (props.initialConfigs && props.initialConfigs.length > 0) {
    props.initialConfigs.forEach((item) => {
      if (item.name !== undefined) {
        initialMap[item.name] = item.value;
      }
    });
  }

  configs.value.forEach((config) => {
    if (initialMap[config.name] !== undefined) {
      configValues[config.name] = initialMap[config.name];
    } else if (configValues[config.name] === undefined) {
      configValues[config.name] = config.value ?? defaultValueForType(config.type);
    }
  });
}

function addPendingConfig() {
  const type = pendingType.value;
  if (props.nodeCode === CONDITION_NODE_CODE && type !== 'Condition') {
    message.warning('条件节点只能添加「条件」类型配置');
    return;
  }
  const name = (pendingName.value || '').trim();
  if (!name) {
    message.warning('请填写配置名称');
    return;
  }
  if (configs.value.some((c) => c.name === name)) {
    message.warning('已存在同名配置');
    return;
  }

  const row = {
    id: Date.now(),
    name,
    des: (pendingDes.value || '').trim() || name,
    type,
    value: defaultValueForType(type),
    required: false,
    parent: 0,
    k: 1,
    quantize: 0,
    _rowKey: generateUUID()
  };
  configs.value.push(row);
  configValues[name] = defaultValueForType(type);
  resetPendingForm();
}

function removeConfig(config) {
  configs.value = configs.value.filter((c) => c._rowKey !== config._rowKey);
  delete configValues[config.name];
}

async function loadConfigs() {
  if (props.nodeCode === undefined || props.nodeCode === null) return;

  loading.value = true;
  errorMsg.value = '';

  try {
    const res = await api.workflow.getNodeConfigs(props.nodeCode);
    let list = Array.isArray(res.data) ? res.data : [];

    if (list.length === 0 && props.initialConfigs?.length > 0) {
      list = mapInitialToConfigs(props.initialConfigs);
    }

    configs.value = normalizeConfigRows(list);
    initConfigValues();
    resetPendingForm();
  } catch (err) {
    errorMsg.value = `加载节点配置失败：${err?.message || '未知错误'}`;
    console.error('[NodeConfigPanel] 加载配置失败', err);
  } finally {
    loading.value = false;
  }
}

watch(() => props.nodeCode, (newCode) => {
  if (newCode !== undefined && newCode !== null) {
    loadConfigs();
  }
}, { immediate: false });

watch(() => props.initialConfigs, () => {
  if (configs.value.length > 0) {
    initConfigValues();
  }
}, { deep: true });

onMounted(() => {
  resetPendingForm();
  loadConfigs();
});
</script>

<template>
  <div class="node-config-panel">
    <div v-if="nodeName || nodeId" class="node-config-meta">
      <span class="node-config-name">{{ nodeName || '节点' }}</span>
      <code v-if="nodeId" class="node-config-id" :title="nodeId">{{ nodeId }}</code>
    </div>
    <div class="node-config-save-row">
      <Button
        type="primary"
        size="small"
        :disabled="loading || !!errorMsg"
        @click="saveConfigs"
      >
        保存配置
      </Button>
      <span class="save-hint">编辑后请点击保存，配置才会写入节点</span>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <Spin tip="加载配置中..." />
    </div>

    <Alert
      v-else-if="errorMsg"
      type="error"
      :message="errorMsg"
      show-icon
    />

    <template v-else>
      <!-- 无预置项时的说明 -->
      <div v-if="configs.length === 0" class="empty-tip-wrap">
        <Empty
          description="该节点暂无预置配置，请添加配置项"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
      </div>

      <!-- 配置行 -->
      <div
        v-for="config in configs"
        v-show="isConfigVisible(config)"
        :key="config._rowKey"
        class="config-row"
      >
        <div class="config-row-head">
          <span class="config-meta">{{ config.des || config.name }}</span>
          <span class="config-type-tag">{{ config.type }}</span>
          <Button
            type="text"
            size="small"
            danger
            class="config-remove"
            @click="removeConfig(config)"
          >
            <DeleteOutlined />
          </Button>
        </div>
        <div class="config-item">
          <component
            :is="getConfigComponent(config.type)"
            v-if="getConfigComponent(config.type)"
            :config="config"
            :model-value="configValues[config.name] !== undefined ? configValues[config.name] : ''"
            v-bind="poolPropsFor(config.type)"
            @update:model-value="(val) => onConfigValueChange(config.name, val)"
          />
          <div v-else class="unknown-config">
            <span class="unknown-label">{{ config.des || config.name }}</span>
            <span class="unknown-type">（未知配置类型：{{ config.type }}）</span>
          </div>
        </div>
      </div>

      <!-- 添加配置 -->
      <div class="add-config-block">
        <div class="add-config-title">
          <PlusOutlined />
          添加配置
        </div>
        <Space direction="vertical" style="width: 100%" :size="8">
          <div class="add-config-line">
            <span class="add-label">类型</span>
            <Select
              v-model:value="pendingType"
              :options="typeOptionsForNode"
              style="width: 100%"
              placeholder="选择类型"
            />
          </div>
          <div class="add-config-line">
            <span class="add-label">名称</span>
            <Input
              v-model:value="pendingName"
              placeholder="配置项名称"
            />
          </div>
          <div class="add-config-line">
            <span class="add-label">描述</span>
            <Input
              v-model:value="pendingDes"
              placeholder="可选，用于界面展示"
            />
          </div>
          <Button type="dashed" block @click="addPendingConfig">
            <PlusOutlined />
            添加此项
          </Button>
        </Space>
        <p v-if="nodeCode === CONDITION_NODE_CODE" class="add-hint">
          条件节点仅允许添加「条件」类型；名称需唯一。
        </p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.node-config-panel {
  width: 100%;
  padding: 8px 0;
}

.node-config-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
}

.node-config-name {
  font-weight: 600;
  color: #262626;
}

.node-config-id {
  font-size: 11px;
  color: #595959;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  max-width: 100%;
  word-break: break-all;
}

.node-config-save-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.save-hint {
  font-size: 12px;
  color: #8c8c8c;
}

.loading-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px 0;
}

.empty-tip-wrap {
  margin-bottom: 16px;
}

.config-row {
  margin-bottom: 16px;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
}

.config-row-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.config-meta {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
  flex: 1;
  min-width: 0;
}

.config-type-tag {
  font-size: 11px;
  color: #1677ff;
  background: #e6f4ff;
  padding: 0 6px;
  border-radius: 4px;
}

.config-remove {
  margin-left: auto;
}

.config-item {
  margin-bottom: 0;
}

.add-config-block {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.add-config-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #262626;
}

.add-config-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}

.add-label {
  flex-shrink: 0;
  width: 36px;
  font-size: 12px;
  color: #8c8c8c;
  padding-top: 4px;
}

.add-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.5;
}

.add-hint code {
  font-size: 11px;
  background: #f5f5f5;
  padding: 0 4px;
  border-radius: 2px;
}

.unknown-config {
  padding: 4px 8px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  font-size: 12px;
}

.unknown-label {
  color: #595959;
  margin-right: 4px;
}

.unknown-type {
  color: #fa8c16;
}
</style>
