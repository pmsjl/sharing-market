<template>
  <div class="admin-page editorial-surface commodity-order-admin">
    <header class="quiet-heading">
      <h1>订单管理</h1>
      <p>查看交易记录与支付状态。</p>
    </header>
    <!-- 查询区域 -->
    <el-card class="admin-search" shadow="never">
      <h2 class="admin-section-title">筛选条件</h2>
      <el-form
        label-position="top"
        class="admin-filter-grid"
        @submit.prevent="getCommodityOrderList"
      >
        <el-form-item label="订单ID">
          <el-input v-model="queryParams.id" placeholder="请输入订单ID" />
        </el-form-item>
        <el-form-item label="商品ID">
          <el-input
            v-model="queryParams.commodityId"
            placeholder="请输入商品ID"
          />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="queryParams.userId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="queryParams.remark" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select
            v-model="queryParams.payStatus"
            placeholder="请选择支付状态"
          >
            <el-option label="已过期" :value="2" />
            <el-option label="已支付" :value="1" />
            <el-option label="未支付" :value="0" />
          </el-select>
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button native-type="submit" type="primary" plain>查询</el-button>
          <el-button
            class="admin-create"
            type="primary"
            @click="showAddDialog"
            :icon="Promotion"
          >
            添加新订单
          </el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 订单列表表格 -->
    <el-card class="admin-data" shadow="never">
      <h2 class="admin-section-title">订单列表</h2>
      <el-table
        :data="commodityOrderList"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="id" label="订单ID" min-width="172" />
        <el-table-column prop="commodityId" label="商品ID" min-width="172" />
        <el-table-column prop="userId" label="用户ID" min-width="172" />
        <el-table-column prop="userName" label="用户名" min-width="160" />
        <el-table-column prop="userPhone" label="用户电话" min-width="140" />
        <el-table-column prop="buyNumber" label="购买数量" min-width="100" />
        <el-table-column
          prop="paymentAmount"
          label="支付金额（校园币）"
          min-width="170"
          ><template #default="{ row }"
            >{{ formatCampusCoin(row.paymentAmount) }} 校园币</template
          ></el-table-column
        >
        <el-table-column prop="payStatus" label="支付状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="payStatusMap[row.payStatus]?.type || 'info'">
              {{ payStatusMap[row.payStatus]?.text || "未知状态" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" />
        <el-table-column prop="createTime" label="创建时间" min-width="176" />
        <el-table-column prop="updateTime" label="更新时间" min-width="176" />
        <el-table-column
          label="操作"
          width="124"
          fixed="right"
          class-name="admin-operations"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="showEditDialog(row.id)"
              >修改</el-button
            >
            <el-popconfirm
              title="你确定要删除该订单吗？"
              @confirm="deleteCommodityOrder(row)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        background
        layout="total, prev, pager, next"
        :pager-count="5"
        :page-sizes="[5, 10, 15, 20]"
        :current-page="paginationConfig.current"
        :total="paginationConfig.total"
        :page-size="paginationConfig.pageSize"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 修改订单的对话框 -->
    <el-dialog
      append-to-body
      title="修改订单"
      v-model="editDialogVisible"
      width="680px"
      @close="resetEditField(editFormRef)"
      class="market-admin-dialog"
    >
      <el-form label-position="top" :model="editForm" ref="editFormRef">
        <el-form-item label="备注" prop="remark">
          <el-input v-model="editForm.remark" />
        </el-form-item>
        <el-form-item label="支付状态" prop="payStatus">
          <el-select v-model="editForm.payStatus" placeholder="请选择支付状态">
            <el-option label="已过期" :value="2" />
            <el-option label="已支付" :value="1" />
            <el-option label="未支付" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetEditField(editFormRef)">取消</el-button>
        <el-button type="primary" @click="editCommodityOrder">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加订单的对话框 -->
    <el-dialog
      append-to-body
      title="添加订单"
      v-model="addDialogVisible"
      width="680px"
      @close="addDialogClosed"
      class="market-admin-dialog"
    >
      <el-form label-position="top" :model="addForm" ref="addFormRef">
        <el-form-item label="商品ID" prop="commodityId">
          <el-input v-model="addForm.commodityId" />
        </el-form-item>
        <el-form-item label="用户ID" prop="userId">
          <el-input v-model="addForm.userId" />
        </el-form-item>
        <el-form-item label="购买数量" prop="buyNumber">
          <el-input v-model="addForm.buyNumber" />
        </el-form-item>
        <el-form-item label="支付金额（校园币）" prop="paymentAmount">
          <el-input v-model="addForm.paymentAmount" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="addForm.remark" />
        </el-form-item>
        <el-form-item label="支付状态" prop="payStatus">
          <el-select v-model="addForm.payStatus" placeholder="请选择支付状态">
            <el-option label="已支付" :value="1" />
            <el-option label="未支付" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addCommodityOrder">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { formatCampusCoin } from "@/utils/marketNavigation";
import { onMounted, ref } from "vue";
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTag,
  FormInstance,
  ElSelect,
  ElOption
} from "element-plus";
import { Promotion } from "@element-plus/icons-vue";
import {
  addCommodityOrderUsingPost,
  deleteCommodityOrderUsingPost,
  getCommodityOrderVoByIdUsingGet,
  listCommodityOrderVoByPageUsingPost,
  updateCommodityOrderUsingPost
} from "@/api/commodityOrderController";

// 查询参数
const queryParams = ref({
  id: undefined,
  commodityId: undefined,
  userId: undefined,
  remark: "",
  payStatus: undefined
});

// 订单列表
const commodityOrderList = ref([]);
const loading = ref<boolean>(false);

// 分页配置
const paginationConfig = ref({
  pageSize: 10,
  total: 0,
  current: 1
});

// 对话框状态
const editDialogVisible = ref(false);
const addDialogVisible = ref(false);

// 表单引用
const editFormRef = ref<FormInstance>();
const addFormRef = ref<FormInstance>();

// 编辑表单数据
const editForm = ref({
  id: 0,
  payStatus: 0,
  remark: ""
});
// 支付状态映射
const payStatusMap: Record<
  number,
  { text: string; type: "success" | "warning" | "danger" | "info" }
> = {
  0: {
    text: "未支付",
    type: "warning"
  },
  1: {
    text: "已支付",
    type: "success"
  },
  2: {
    text: "已过期",
    type: "danger"
  }
};
// 添加表单数据
const addForm = ref({
  commodityId: 0,
  userId: 0,
  buyNumber: 0,
  paymentAmount: 0,
  remark: "",
  payStatus: 0
});

// 获取订单列表
const getCommodityOrderList = async () => {
  loading.value = true;
  try {
    const res = await listCommodityOrderVoByPageUsingPost({
      ...queryParams.value,
      current: paginationConfig.value.current,
      pageSize: paginationConfig.value.pageSize
    });
    if (res.code === 200) {
      commodityOrderList.value = res.data.records;
      paginationConfig.value.total = parseInt(res.data.total);
    } else {
      ElMessage.error("获取订单列表失败");
    }
  } catch (error) {
    ElMessage.error("获取订单列表失败");
  } finally {
    loading.value = false;
  }
};

// 重置查询条件
const resetQuery = () => {
  queryParams.value = {
    id: undefined,
    commodityId: undefined,
    userId: undefined,
    remark: "",
    payStatus: undefined
  };
  getCommodityOrderList();
};

// 显示修改对话框
const showEditDialog = async (id?: string) => {
  if (!id) return;
  loading.value = true;
  try {
    const res = await getCommodityOrderVoByIdUsingGet({ id });
    if (res.code === 200) {
      editForm.value.id = res.data.id;
      editForm.value.payStatus = res.data.payStatus;
      editForm.value.remark = res.data.remark;
      editDialogVisible.value = true;
    } else {
      ElMessage.error("获取订单信息失败");
    }
  } catch (error) {
    ElMessage.error("获取订单信息失败");
  } finally {
    loading.value = false;
  }
};

// 修改订单
const editCommodityOrder = async () => {
  try {
    const res = await updateCommodityOrderUsingPost(editForm.value);
    if (res.code === 200) {
      ElMessage.success("修改订单成功");
      editDialogVisible.value = false;
      await getCommodityOrderList();
    } else {
      ElMessage.error("修改订单失败");
    }
  } catch (error) {
    ElMessage.error("修改订单失败");
  }
};

// 添加订单
const addCommodityOrder = async () => {
  try {
    const res = await addCommodityOrderUsingPost(addForm.value);
    if (res.code === 200) {
      ElMessage.success("添加订单成功");
      addDialogVisible.value = false;
      await getCommodityOrderList();
    } else {
      ElMessage.error("添加订单失败");
    }
  } catch (error) {
    ElMessage.error("添加订单失败");
  }
};

// 删除订单
const deleteCommodityOrder = async (row) => {
  try {
    const res = await deleteCommodityOrderUsingPost({ id: row.id });
    if (res.code === 200) {
      ElMessage.success("删除订单成功");
      await getCommodityOrderList();
    } else {
      ElMessage.error("删除订单失败");
    }
  } catch (error) {
    ElMessage.error("删除订单失败");
  }
};

// 关闭添加对话框
const addDialogClosed = () => {
  addForm.value = {
    commodityId: 0,
    userId: 0,
    buyNumber: 0,
    paymentAmount: 0,
    remark: "",
    payStatus: 0
  };
};

// 关闭修改对话框
const resetEditField = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  editDialogVisible.value = false;
  formEl.resetFields();
};

// 分页处理
const handleSizeChange = (size: number) => {
  paginationConfig.value.pageSize = size;
  getCommodityOrderList();
};

const handleCurrentChange = (page: number) => {
  paginationConfig.value.current = page;
  getCommodityOrderList();
};

// 取消删除

// 显示添加对话框
const showAddDialog = () => {
  addDialogVisible.value = true;
};

// 初始化加载订单列表
onMounted(() => {
  getCommodityOrderList();
});
</script>

<style scoped></style>
