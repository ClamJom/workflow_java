<script setup>
import {
    Layout,
    LayoutSider,
    LayoutContent,
    Button,
    Tooltip,
    Popconfirm,
    message,
    Empty,
    Spin,
} from "ant-design-vue";
import {
    PlusOutlined,
    DeleteOutlined,
    FileTextOutlined,
} from "@ant-design/icons-vue";
import {onMounted, ref} from "vue";
import api from "../api";
import WorkflowPreview from "../components/WorkflowPreview.vue";
import {generateUUID} from "../utils/token.js";
import {NODE_TYPE_CODE} from "../components/nodes/index.js";

const workflowApis = api.workflow;

/** 工作流列表（仅包含摘要信息，不含节点详情） */
const workflows = ref([]);
/** 当前选中的工作流 UUID */
const currentUUID = ref(null);
/** 列表加载状态 */
const listLoading = ref(false);
/** 新建工作流加载状态 */
const creating = ref(false);

/**
 * 子组件保存工作流成功后刷新左侧列表名称
 */
function onWorkflowSaved() {
    getAllWorkflows();
}

/**
 * 获取所有工作流摘要列表
 */
async function getAllWorkflows() {
    listLoading.value = true;
    try {
        const res = await workflowApis.getWorkflows();
        workflows.value = res.data || [];
    } catch (err) {
        message.error('获取工作流列表失败');
        console.error('[App] 获取工作流列表失败', err);
    } finally {
        listLoading.value = false;
    }
}

/**
 * 点击工作流列表项，切换当前工作流
 * @param {string} uuid
 */
function handleWorkflowClicked(uuid) {
    currentUUID.value = uuid;
}

/**
 * 新建工作流
 */
async function handleNewWorkflow() {
    creating.value = true;
    try {
        const startId = generateUUID();
        const endId = generateUUID();
        const workflow = {
            name: '新建工作流',
            nodes: [
                {
                    id: startId,
                    name: '开始',
                    type: NODE_TYPE_CODE.START,
                    configs: [],
                    position: {x: 80, y: 200},
                },
                {
                    id: endId,
                    name: '结束',
                    type: NODE_TYPE_CODE.END,
                    configs: [],
                    position: {x: 400, y: 200},
                },
            ],
            edges: [{from: startId, to: endId}],
        };
        await workflowApis.saveWorkflow(workflow);
        await getAllWorkflows();
        // 自动选中最新创建的工作流（列表末尾）
        if (workflows.value.length > 0) {
            currentUUID.value = workflows.value[workflows.value.length - 1].uuid;
        }
    } catch (err) {
        message.error('新建工作流失败');
        console.error('[App] 新建工作流失败', err);
    } finally {
        creating.value = false;
    }
}

/**
 * 删除工作流
 * @param {string} uuid
 */
async function handleDeleteWorkflow(uuid) {
    try {
        await workflowApis.deleteWorkflow(uuid);
        message.success('已删除');
        // 若删除的是当前选中的工作流，则清空选中
        if (currentUUID.value === uuid) {
            currentUUID.value = null;
        }
        await getAllWorkflows();
    } catch (err) {
        message.error('删除工作流失败');
        console.error('[App] 删除工作流失败', err);
    }
}

/**
 * 如果是开发环境，将 API 挂载到 window 上，方便调试
 */
function hookApiToWindowIfDev() {
    if (import.meta.env.PROD) return;
    window.workflowApis = workflowApis;
}

onMounted(() => {
    hookApiToWindowIfDev();
    getAllWorkflows();
});
</script>

<template>
  <Layout class="app-layout">
    <!-- 左侧工作流列表 -->
    <LayoutSider class="sider" :width="220" theme="light">
      <div class="sider-header">
        <span class="sider-title">工作流</span>
        <Tooltip title="新建工作流">
          <Button
            type="primary"
            size="small"
            shape="circle"
            :loading="creating"
            @click="handleNewWorkflow"
          >
            <template #icon><PlusOutlined /></template>
          </Button>
        </Tooltip>
      </div>

      <div class="sider-body">
        <Spin :spinning="listLoading">
          <!-- 空状态 -->
          <Empty
            v-if="!listLoading && workflows.length === 0"
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
            description="暂无工作流"
            class="empty-tip"
          />

          <!-- 工作流列表 -->
          <ul v-else class="workflow-list">
            <li
              v-for="workflow in workflows"
              :key="workflow.uuid"
              class="workflow-item"
              :class="{ active: currentUUID === workflow.uuid }"
              @click="handleWorkflowClicked(workflow.uuid)"
            >
              <FileTextOutlined class="item-icon" />
              <span class="item-name" :title="workflow.name || '未命名工作流'">
                {{ workflow.name || '未命名工作流' }}
              </span>
              <Popconfirm
                title="确认删除该工作流？"
                ok-text="删除"
                cancel-text="取消"
                ok-type="danger"
                @confirm.stop="handleDeleteWorkflow(workflow.uuid)"
              >
                <Button
                  type="text"
                  size="small"
                  danger
                  class="delete-btn"
                  @click.stop
                >
                  <template #icon><DeleteOutlined /></template>
                </Button>
              </Popconfirm>
            </li>
          </ul>
        </Spin>
      </div>
    </LayoutSider>

    <!-- 右侧工作流预览/编辑区 -->
    <LayoutContent class="content">
      <!-- 未选中工作流时的提示 -->
      <div v-if="!currentUUID" class="no-selection">
        <Empty
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          description="请从左侧选择工作流，或新建一个工作流"
        />
      </div>

      <!-- 工作流预览组件 -->
      <WorkflowPreview
        v-else
        :key="currentUUID"
        :uuid="currentUUID"
        class="preview"
        @saved="onWorkflowSaved"
      />
    </LayoutContent>
  </Layout>
</template>

<style scoped>
.app-layout {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* ── 左侧边栏 ── */
.sider {
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f0f0f0;
  overflow: hidden;
}

.sider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.sider-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
}

.sider-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.empty-tip {
  padding: 24px 0;
}

/* ── 工作流列表 ── */
.workflow-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.workflow-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 0;
  transition: background 0.15s;
  position: relative;
}

.workflow-item:hover {
  background: #f5f5f5;
}

.workflow-item.active {
  background: #e6f4ff;
  color: #1677ff;
}

.item-icon {
  flex-shrink: 0;
  font-size: 14px;
  color: #8c8c8c;
}

.workflow-item.active .item-icon {
  color: #1677ff;
}

.item-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.workflow-item:hover .delete-btn {
  opacity: 1;
}

/* ── 右侧内容区 ── */
.content {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fafafa;
}

.no-selection {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.preview {
  width: 100%;
  height: 100%;
}
</style>
