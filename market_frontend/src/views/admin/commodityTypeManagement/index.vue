<template>
  <div class="admin-page editorial-surface commodity-type-admin">
    <header class="quiet-heading">
      <h1>商品分类</h1>
      <p>整理好物分类，方便同学查找。</p>
    </header>
    <!-- 查询区域 -->
    <el-card class="admin-search" shadow="never">
      <h2 class="admin-section-title">筛选条件</h2>
      <el-form
        label-position="top"
        class="admin-filter-grid"
        @submit.prevent="getCommodityTypeList"
      >
        <el-form-item label="类别ID">
          <el-input v-model="queryParams.id" placeholder="请输入类别ID" />
        </el-form-item>
        <el-form-item label="类别名称">
          <el-input
            v-model="queryParams.typeName"
            placeholder="请输入类别名称"
          />
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button native-type="submit" type="primary" plain>查询 </el-button>
          <el-button
            class="admin-create"
            type="primary"
            @click="showAddDialog"
            :icon="Promotion"
          >
            添加新类别
          </el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 商品类别列表表格 -->
    <el-card class="admin-data" shadow="never">
      <h2 class="admin-section-title">分类列表</h2>
      <el-table
        :data="commodityTypeList"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="id" label="类别ID" min-width="172" />
        <el-table-column prop="typeName" label="类别名称" min-width="160" />
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
              >修改
            </el-button>
            <el-popconfirm
              title="你确定要删除该类别吗？"
              @confirm="deleteCommodityType(row)"
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

    <!-- 修改类别的对话框 -->
    <el-dialog
      append-to-body
      title="修改类别"
      v-model="editDialogVisible"
      width="680px"
      @close="resetEditField(editFormRef)"
      class="market-admin-dialog"
    >
      <el-form label-position="top" :model="editForm" ref="editFormRef">
        <el-form-item label="类别名称" prop="typeName">
          <el-input v-model="editForm.typeName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetEditField(editFormRef)">取消</el-button>
        <el-button type="primary" @click="editCommodityType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加类别的对话框 -->
    <el-dialog
      append-to-body
      title="添加类别"
      v-model="addDialogVisible"
      width="680px"
      @close="addDialogClosed"
      class="market-admin-dialog"
    >
      <el-form label-position="top" :model="addForm" ref="addFormRef">
        <el-form-item label="类别名称" prop="typeName">
          <el-input v-model="addForm.typeName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addCommodityType">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  FormInstance
} from "element-plus";
import { Promotion } from "@element-plus/icons-vue";
import {
  addCommodityTypeUsingPost,
  deleteCommodityTypeUsingPost,
  getCommodityTypeVoByIdUsingGet,
  listCommodityTypeVoByPageUsingPost,
  updateCommodityTypeUsingPost
} from "@/api/commodityTypeController";

// 查询参数
const queryParams = ref({
  id: undefined,
  typeName: ""
});

// 商品类别列表
const commodityTypeList = ref([]);
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
  typeName: ""
});

// 添加表单数据
const addForm = ref({
  typeName: ""
});

// 获取商品类别列表
const getCommodityTypeList = async () => {
  loading.value = true;
  try {
    const res = await listCommodityTypeVoByPageUsingPost({
      ...queryParams.value,
      current: paginationConfig.value.current,
      pageSize: paginationConfig.value.pageSize
    });
    if (res.code === 200) {
      commodityTypeList.value = res.data.records;
      paginationConfig.value.total = parseInt(res.data.total);
    } else {
      ElMessage.error("获取商品类别列表失败");
    }
  } catch (error) {
    ElMessage.error("获取商品类别列表失败");
  } finally {
    loading.value = false;
  }
};

// 重置查询条件
const resetQuery = () => {
  queryParams.value = {
    id: undefined,
    typeName: ""
  };
  getCommodityTypeList();
};

// 显示修改对话框
const showEditDialog = async (id?: string) => {
  if (!id) return;
  loading.value = true;
  try {
    const res = await getCommodityTypeVoByIdUsingGet({ id });
    if (res.code === 200) {
      editForm.value.id = res.data.id;
      editForm.value.typeName = res.data.typeName;
      editDialogVisible.value = true;
    } else {
      ElMessage.error("获取商品类别信息失败");
    }
  } catch (error) {
    ElMessage.error("获取商品类别信息失败");
  } finally {
    loading.value = false;
  }
};

// 修改商品类别
const editCommodityType = async () => {
  try {
    const res = await updateCommodityTypeUsingPost(editForm.value);
    if (res.code === 200) {
      ElMessage.success("修改商品类别成功");
      editDialogVisible.value = false;
      await getCommodityTypeList();
    } else {
      ElMessage.error("修改商品类别失败");
    }
  } catch (error) {
    ElMessage.error("修改商品类别失败");
  }
};

// 添加商品类别
const addCommodityType = async () => {
  try {
    const res = await addCommodityTypeUsingPost(addForm.value);
    if (res.code === 200) {
      ElMessage.success("添加商品类别成功");
      addDialogVisible.value = false;
      await getCommodityTypeList();
    } else {
      ElMessage.error("添加商品类别失败");
    }
  } catch (error) {
    ElMessage.error("添加商品类别失败");
  }
};

// 删除商品类别
const deleteCommodityType = async (row) => {
  try {
    const res = await deleteCommodityTypeUsingPost({ id: row.id });
    if (res.code === 200) {
      ElMessage.success("删除商品类别成功");
      await getCommodityTypeList();
    } else {
      ElMessage.error(res.message || "删除商品类别失败");
    }
  } catch (error) {
    ElMessage.error(error.message || "删除商品类别失败");
  }
};

// 关闭添加对话框
const addDialogClosed = () => {
  addForm.value = {
    typeName: ""
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
  getCommodityTypeList();
};

const handleCurrentChange = (page: number) => {
  paginationConfig.value.current = page;
  getCommodityTypeList();
};

// 取消删除

// 显示添加对话框
const showAddDialog = () => {
  addDialogVisible.value = true;
};

// 初始化加载商品类别列表
onMounted(() => {
  getCommodityTypeList();
});
</script>
<style scoped lang="scss"></style>
