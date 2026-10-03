<template>
  <div class="admin-page editorial-surface">
    <header class="quiet-heading">
      <h1>用户管理</h1>
      <p>维护账户资料、角色与校园币。</p>
    </header>
    <el-card class="admin-search" shadow="never">
      <h2 class="admin-section-title">筛选条件</h2>
      <el-form
        label-position="top"
        class="admin-filter-grid"
        @submit.prevent="getUserList"
      >
        <el-form-item label="用户名">
          <el-input
            v-model="searchParams.userName"
            placeholder="请输入用户名"
          />
        </el-form-item>
        <el-form-item label="用户简介">
          <el-input
            v-model="searchParams.userProfile"
            placeholder="请输入用户简介"
          />
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button @click="resetSearchParams">重置</el-button>
          <el-button native-type="submit" type="primary" plain>查询 </el-button>
        </div>
      </el-form>
    </el-card>
    <el-card class="admin-data" shadow="never">
      <h2 class="admin-section-title">用户列表</h2>
      <el-table
        v-loading="loading"
        :data="userList"
        border
        style="width: 100%"
        :pagination="pagination"
      >
        <el-table-column type="index" width="48"></el-table-column>
        <el-table-column
          label="用户名"
          prop="userName"
          :copyable="true"
          min-width="160"
        ></el-table-column>
        <el-table-column
          label="用户账户"
          prop="userAccount"
          :copyable="true"
          min-width="100"
        ></el-table-column>
        <el-table-column label="头像" prop="userAvatar" min-width="100">
          <template #default="{ row }">
            <el-image
              :src="row.userAvatar"
              style="width: 48px; height: 48px"
              fit="cover"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="用户简介"
          prop="userProfile"
          :copyable="true"
          min-width="220"
        ></el-table-column>
        <el-table-column label="角色" prop="userRole" min-width="100">
          <template #default="{ row }">
            <el-select v-model="row.userRole" placeholder="选择角色" disabled>
              <el-option
                v-for="(item, value) in roleEnum"
                :key="value"
                :label="item.text"
                :value="value"
              ></el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="校园币" prop="balance" min-width="150">
          <template #default="{ row }">{{
            formatCampusCoin(row.balance, true)
          }}</template>
        </el-table-column>
        <el-table-column
          label="创建时间"
          prop="createTime"
          sortable
          :formatter="formatDate"
          min-width="176"
        ></el-table-column>
        <el-table-column
          label="操作"
          width="242"
          fixed="right"
          class-name="admin-operations"
        >
          <template #default="{ row }">
            <el-button link @click="edit(row)">编辑</el-button>
            <el-button link type="primary" @click="view(row)"> 查看</el-button>
            <el-button link type="success" @click="openGrantDialog(row)"
              >发放校园币</el-button
            >
            <el-popconfirm
              title="你确定要删除该用户吗？"
              @confirm="deleteUser(row)"
            >
              <template #reference>
                <el-button link type="danger"> 删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        background
        layout="total, prev, pager, next"
        :pager-count="5"
        :page-sizes="[5, 10, 15, 20]"
        :current-page="pagination.currentPage"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      append-to-body
      title="编辑用户"
      v-model="editDialogVisible"
      width="680px"
      @close="resetEditField(editFormRef)"
      class="market-admin-dialog"
    >
      <el-form label-position="top" :model="editForm" ref="editFormRef">
        <el-form-item
          label="用户名"
          :label-width="formLabelWidth"
          label-position="left"
        >
          <el-input v-model="editForm.userName" autocomplete="off" />
        </el-form-item>
        <el-form-item
          label="用户头像"
          :label-width="formLabelWidth"
          label-position="left"
        >
          <el-input v-model="editForm.userAvatar" autocomplete="off" />
        </el-form-item>
        <el-form-item
          label="用户简介"
          :label-width="formLabelWidth"
          label-position="left"
        >
          <el-input v-model="editForm.userProfile" autocomplete="off" />
        </el-form-item>
        <el-form-item
          label="角色"
          :label-width="formLabelWidth"
          label-position="left"
        >
          <el-select
            v-model="editForm.userRole"
            placeholder="选择角色"
            clearable
            readonly
          >
            <el-option
              v-for="(item, value) in roleEnum"
              :key="value"
              :label="item.text"
              :value="value"
            ></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <span class="dialog-footer">
        <slot name="footer">
          <el-button @click="resetEditField(editFormRef)">取消</el-button>
          <el-button type="primary" @click="saveEditForm">保存</el-button></slot
        >
      </span>
    </el-dialog>

    <el-dialog
      append-to-body
      title="发放校园币"
      v-model="grantDialogVisible"
      width="420px"
      class="market-admin-dialog"
    >
      <el-form label-position="top" :model="grantForm">
        <el-form-item label="接收用户">
          <el-input :model-value="grantUserName" disabled />
        </el-form-item>
        <el-form-item label="发放数量">
          <el-input-number
            v-model="grantForm.amount"
            :min="0.01"
            :max="100000"
            :precision="2"
            :step="100"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="发放原因">
          <el-input
            v-model="grantForm.reason"
            maxlength="200"
            show-word-limit
            type="textarea"
            placeholder="例如：活动奖励"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="granting" @click="submitGrant">
          确认发放
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog
      append-to-body
      title="查看用户"
      v-model="viewDialogVisible"
      width="680px"
      class="market-admin-dialog"
    >
      <el-form
        label-position="top"
        :model="viewForm"
        ref="viewFormRef"
        disabled
      >
        <el-form-item label="用户名" :label-width="formLabelWidth">
          <el-input v-model="viewForm.userName" readonly />
        </el-form-item>
        <el-form-item label="用户账户" :label-width="formLabelWidth">
          <el-input v-model="viewForm.userAccount" readonly />
        </el-form-item>
        <el-form-item label="用户简介" :label-width="formLabelWidth">
          <el-input v-model="viewForm.userProfile" readonly />
        </el-form-item>
        <el-form-item label="角色" :label-width="formLabelWidth">
          <el-select
            v-model="editForm.userRole"
            placeholder="选择角色"
            clearable
            readonly
          >
            <el-option
              v-for="(item, value) in roleEnum"
              :key="value"
              :label="item.text"
              :value="value"
            ></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <span class="dialog-footer">
        <slot name="footer">
          <el-button @click="viewDialogVisible = false">关闭</el-button></slot
        >
      </span>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { formatCampusCoin } from "@/utils/marketNavigation";
import { onMounted, ref } from "vue";
import {
  ElButton,
  ElImage,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  FormInstance
} from "element-plus";
import {
  deleteUserUsingPost,
  listUserByPageUsingPost,
  updateUserUsingPost
} from "@/api/userController";
import { grantCampusCoin } from "@/api/campusCoinController";

const loading = ref(false);
const userList = ref([]);
const pagination = ref({
  currentPage: 1,
  pageSize: 5,
  total: 0
});
const searchParams = ref({
  userName: "",
  userProfile: ""
});
const formLabelWidth = "80px";
const roleEnum = {
  user: { text: "普通用户", status: "Default" },
  admin: { text: "管理员", status: "Success" }
};

// 表单数据
const editDialogVisible = ref(false);
const viewDialogVisible = ref(false);
const grantDialogVisible = ref(false);
const granting = ref(false);
const grantUserName = ref("");
const grantForm = ref({ userId: "", amount: 500, reason: "" });
const editForm = ref({
  id: 0,
  userName: "",
  userAccount: "",
  userProfile: "",
  userRole: ""
});
const editFormRef = ref<FormInstance>();
const viewForm = ref();
// 格式化时间
const formatDate = (row, column, cellValue) => {
  return new Date(cellValue).toLocaleString();
};
const handleSizeChange = (val: number) => {
  pagination.value.pageSize = val;
  getUserList();
};
// 获取用户数据
const getUserList = async () => {
  loading.value = true;
  try {
    const res = await listUserByPageUsingPost({
      current: pagination.value.currentPage,
      pageSize: pagination.value.pageSize,
      userProfile: searchParams.value.userProfile,
      userName: searchParams.value.userName
    });
    if (res.code !== 200) {
      ElMessage.error(res.msg);
      return;
    }
    userList.value = res.data.records || [];
    pagination.value.total = parseInt(res.data.total) || 0;
  } catch (error: any) {
    ElMessage.error("获取用户列表失败，" + error.message);
  } finally {
    loading.value = false;
  }
};
// 编辑用户
const edit = (row) => {
  editDialogVisible.value = true;
  Object.assign(editForm.value, row);
};

const openGrantDialog = (row) => {
  grantUserName.value = row.userName || row.userAccount || String(row.id);
  grantForm.value = { userId: String(row.id), amount: 500, reason: "" };
  grantDialogVisible.value = true;
};

const submitGrant = async () => {
  if (!grantForm.value.reason.trim()) {
    ElMessage.warning("请填写发放原因");
    return;
  }
  granting.value = true;
  try {
    const res = await grantCampusCoin({
      ...grantForm.value,
      reason: grantForm.value.reason.trim()
    });
    if (res.code !== 200 || !res.data) {
      ElMessage.error(res.message || "发放失败");
      return;
    }
    ElMessage.success("校园币发放成功");
    grantDialogVisible.value = false;
    await getUserList();
  } catch (error: any) {
    ElMessage.error("发放失败，" + error.message);
  } finally {
    granting.value = false;
  }
};

// 保存编辑
const saveEditForm = async () => {
  try {
    const res = await updateUserUsingPost({
      ...editForm.value
    });
    if (res.code === 200) {
      ElMessage.success("编辑成功");
      editDialogVisible.value = false;
      await getUserList();
    } else {
      ElMessage.error("编辑失败");
    }
  } catch (error: any) {
    ElMessage.error("编辑失败，" + error.message);
  }
};
const resetEditField = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  editDialogVisible.value = false;
  formEl.resetFields();
};

// 查看用户
const view = (row) => {
  viewForm.value = { ...row };
  viewDialogVisible.value = true;
};
const deleteUser = async (row) => {
  try {
    const res = await deleteUserUsingPost({
      id: row.id
    });
    if (res.code === 200) {
      ElMessage.success("删除成功");
      await getUserList();
    } else {
      ElMessage.error("删除失败");
    }
  } catch (error: any) {
    ElMessage.error("删除失败，" + error.message);
  }
};
// 分页改变时触发
const handleCurrentChange = (page) => {
  pagination.value.currentPage = page;
  getUserList();
};
const resetSearchParams = () => {
  searchParams.value.userName = "";
  searchParams.value.userProfile = "";
};
// 页面挂载时初始化数据
onMounted(() => {
  getUserList();
});
</script>

<style scoped lang="scss"></style>
