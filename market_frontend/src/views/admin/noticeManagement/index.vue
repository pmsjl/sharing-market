<template>
  <div class="admin-page editorial-surface search_container">
    <header class="quiet-heading">
      <h1>公告管理</h1>
      <p>发布和维护校园公告。</p>
    </header>
    <el-card class="admin-data" shadow="never">
      <div class="admin-section-header">
        <h2 class="admin-section-title">公告列表</h2>
        <el-button type="primary" @click="showAddDialog()" :icon="Promotion">
          发布新公告
        </el-button>
      </div>
      <!-- 表格区域 -->
      <el-table
        :data="tableData"
        border
        style="width: 100%"
        stripe
        v-loading="loading"
        element-loading-text="正在加载"
        element-loading-spinner="el-icon-loading"
      >
        <el-table-column prop="id" label="ID" min-width="172"></el-table-column>
        <el-table-column
          prop="noticeTitle"
          label="标题"
          min-width="160"
        ></el-table-column>
        <el-table-column
          prop="noticeContent"
          label="公告"
          min-width="220"
        ></el-table-column>
        <el-table-column label="发布人" min-width="100">
          <template #default="{ row }">
            <div class="notice-admin-cell">
              <el-avatar :size="28" :src="getNoticePublisherAvatar(row)">
                {{ getNoticePublisherInitial(row) }}
              </el-avatar>
              <span>{{ getNoticePublisherName(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="发布日期"
          min-width="176"
        ></el-table-column>
        <el-table-column
          label="操作"
          width="124"
          fixed="right"
          class-name="admin-operations"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="showEditDialog(row.id)">
              修改
            </el-button>
            <el-popconfirm
              title="你确定要删除该公告吗？"
              @confirm="deleteNotice(row)"
            >
              <template #reference>
                <el-button link type="danger"> 删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页查询区域 -->
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="pagination.currentPage"
        :page-sizes="[1, 2, 3, 4, 5]"
        :page-size="pagination.pageSize"
        layout="total, prev, pager, next"
        :pager-count="5"
        :total="total"
      />
      <!-- 修改公告的对话框 -->
      <el-dialog
        append-to-body
        title="修改公告"
        v-model="editDialogVisible"
        width="680px"
        @close="resetEditField(editFormRef)"
        class="market-admin-dialog"
      >
        <el-form
          label-position="top"
          :model="editForm"
          ref="editFormRef"
          :rules="editFormRules"
        >
          <el-form-item label="公告标题" prop="noticeTitle">
            <el-input v-model="editForm.noticeTitle"></el-input>
          </el-form-item>
          <el-form-item label="公告内容" prop="noticeContent">
            <el-input
              type="textarea"
              v-model="editForm.noticeContent"
            ></el-input>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="resetEditField(editFormRef)">取 消</el-button>
          <el-button type="primary" @click="editNoticeById">确 定</el-button>
        </template>
      </el-dialog>
      <!-- 添加公告的对话框 -->
      <el-dialog
        append-to-body
        title="添加公告"
        v-model="addDialogVisible"
        width="680px"
        @close="addDialogClosed"
        class="market-admin-dialog"
      >
        <el-form
          label-position="top"
          :model="addForm"
          ref="addFormRef"
          :rules="addFormRules"
        >
          <el-form-item label="公告标题" prop="noticeTitle">
            <el-input v-model="addForm.noticeTitle"></el-input>
          </el-form-item>
          <el-form-item label="公告内容" prop="noticeContent">
            <el-input
              type="textarea"
              v-model="addForm.noticeContent"
            ></el-input>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addDialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="addNotice">添加公告</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Promotion } from "@element-plus/icons-vue";
import { onMounted, ref } from "vue";
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
  FormInstance
} from "element-plus";
import {
  addNoticeUsingPost,
  deleteNoticeUsingPost,
  getNoticeVoByIdUsingGet,
  listNoticeVoByPageUsingPost,
  updateNoticeUsingPost
} from "@/api/noticeController";

const tableData = ref([]);
const editDialogVisible = ref(false);
const addDialogVisible = ref(false);
const editForm = ref({
  id: 0,
  noticeTitle: "",
  noticeContent: ""
});
const addForm = ref({
  noticeTitle: "",
  noticeContent: ""
});
const pagination = ref({
  currentPage: 1,
  pageSize: 5,
  total: 0
});
const editFormRef = ref<FormInstance>();
const total = ref(0);
const loading = ref(true);
const getNoticePublisherName = (notice: API.NoticeVO) => {
  return (
    notice?.user?.userName ||
    (notice?.noticeAdminId ? `管理员 ${notice.noticeAdminId}` : "管理员")
  );
};
const getNoticePublisherAvatar = (notice: API.NoticeVO) => {
  return notice?.user?.userAvatar || "";
};
const getNoticePublisherInitial = (notice: API.NoticeVO) => {
  return getNoticePublisherName(notice).slice(0, 1);
};
const editFormRules = {
  noticeTitle: [
    { required: true, message: "请输入公告标题", trigger: "blur" },
    { min: 4, max: 30, message: "长度在4到30个字符", trigger: "blur" }
  ],
  noticeContent: [
    { required: true, message: "请输入公告内容", trigger: "blur" }
  ]
};
const addFormRules = {
  noticeTitle: [
    { required: true, message: "请输入公告标题", trigger: "blur" },
    { min: 4, max: 30, message: "长度在4到30个字符", trigger: "blur" }
  ],
  noticeContent: [
    { required: true, message: "请输入公告内容", trigger: "blur" }
  ]
};

// 获取公告列表
const getNoticeList = async () => {
  loading.value = true;
  try {
    const res = await listNoticeVoByPageUsingPost({
      current: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    });
    if (res.code !== 200) {
      total.value = 0;
      loading.value = false;
      ElMessage.error({
        duration: 1000,
        message: "获取公告列表失败"
      });
      return;
    }
    tableData.value = res.data.records;
    total.value = parseInt(res.data.total);
    loading.value = false;
  } catch (error) {
    ElMessage.error("获取数据失败");
    loading.value = false;
  }
};

// 显示修改公告的对话框
const showEditDialog = async (id?: string) => {
  if (!id) return;
  loading.value = true;
  try {
    const res = await getNoticeVoByIdUsingGet({
      id
    });
    if (res.code !== 200) {
      return ElMessage.error({
        duration: 1000,
        message: "获取该公告信息失败"
      });
    }
    editForm.value.id = id;
    editForm.value.noticeContent = res.data.noticeContent;
    editForm.value.noticeTitle = res.data.noticeTitle;
    editDialogVisible.value = true;
  } catch (error) {
    ElMessage.error("获取公告数据失败");
  } finally {
    loading.value = false;
  }
};

// 关闭修改对话框
const resetEditField = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  editDialogVisible.value = false;
  formEl.resetFields();
};

// 修改公告
const editNoticeById = async () => {
  try {
    const res = await updateNoticeUsingPost({
      id: editForm.value.id,
      noticeContent: editForm.value.noticeContent,
      noticeTitle: editForm.value.noticeTitle
    });
    if (res.code !== 200) {
      ElMessage.error({
        duration: 1000,
        message: "修改公告失败"
      });
      return;
    }
    editDialogVisible.value = false;
    ElMessage.success({
      duration: 1000,
      message: "修改公告成功"
    });
    editForm.value.noticeTitle = "";
    editForm.value.noticeContent = "";
    await getNoticeList();
  } catch (error) {
    ElMessage.error("修改公告失败");
  }
};

// 删除公告
const deleteNotice = async (row) => {
  try {
    const res = await deleteNoticeUsingPost({
      id: row.id
    });
    if (res.code === 200) {
      ElMessage.success("删除成功");
      await getNoticeList();
    } else {
      ElMessage.error("删除失败");
    }
  } catch (error: any) {
    ElMessage.error("删除失败，" + error.message);
  }
};
// 显示添加公告的对话框
const showAddDialog = () => {
  addDialogVisible.value = true;
};

// 关闭添加对话框
const addDialogClosed = () => {
  addForm.value = { noticeTitle: "", noticeContent: "" };
};

// 添加公告
const addNotice = async () => {
  try {
    const res = await addNoticeUsingPost({ ...addForm.value });
    if (res.code !== 200) {
      return ElMessage.error({
        duration: 1000,
        message: "添加公告失败"
      });
    }
    ElMessage.success({
      duration: 1000,
      message: "添加公告成功"
    });
    addDialogVisible.value = false;
    await getNoticeList();
  } catch (error) {
    ElMessage.error("添加公告失败");
  }
};

// 分页变化
const handleSizeChange = (val) => {
  pagination.value.pageSize = val;
  getNoticeList();
};

const handleCurrentChange = (val) => {
  pagination.value.currentPage = val;
  getNoticeList();
};
// 页面挂载时初始化
onMounted(() => {
  getNoticeList();
});
</script>

<style scoped>
.notice-admin-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.notice-admin-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
