<template>
  <div class="market-page commodity-page" ref="pageRef">
    <div class="market-page-header">
      <div>
        <span class="market-eyebrow">MARKET BOARD</span>
        <h1 class="market-title">商品公告栏</h1>
        <p class="market-subtitle">
          按名称、分类、成色和库存查找校园好物，也可以把自己的闲置贴到公告栏。
        </p>
      </div>
      <el-button type="primary" @click="addDialogVisible = true">
        发布商品
      </el-button>
    </div>

    <el-card class="market-filter-card">
      <div class="market-form-grid">
        <el-form-item label="商品名称">
          <el-input
            v-model="queryParams.commodityName"
            placeholder="例如：高数教材"
          />
        </el-form-item>
        <el-form-item label="商品简介">
          <el-input
            v-model="queryParams.commodityDescription"
            placeholder="关键词、品牌或用途"
          />
        </el-form-item>
        <el-form-item label="新旧程度">
          <el-input v-model="queryParams.degree" placeholder="例如：九成新" />
        </el-form-item>
        <el-form-item label="库存数量">
          <el-input
            v-model="queryParams.commodityInventory"
            placeholder="输入库存数量"
          />
        </el-form-item>
        <el-form-item label="商品分类">
          <el-select
            v-model="queryParams.commodityTypeId"
            placeholder="请选择商品分类"
            clearable
          >
            <el-option
              v-for="type in commodityTypeList"
              :key="type.id"
              :label="type.typeName"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <div class="market-form-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="getCommodityList">查询</el-button>
        </div>
      </div>
    </el-card>

    <CommodityList :commodityList="commodityList" />

    <div class="market-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[5, 10, 15, 20]"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog
      title="发布商品"
      v-model="addDialogVisible"
      width="560px"
      @close="resetAddForm"
    >
      <el-form :model="addForm" ref="addFormRef" label-width="100px">
        <el-form-item label="商品名称" prop="commodityName">
          <el-input
            v-model="addForm.commodityName"
            placeholder="请输入商品名称"
          />
        </el-form-item>
        <el-form-item label="商品简介" prop="commodityDescription">
          <el-input
            type="textarea"
            v-model="addForm.commodityDescription"
            placeholder="写清品牌、成色、适用场景"
            :rows="4"
          />
        </el-form-item>
        <el-form-item label="商品封面" prop="commodityAvatar">
          <div class="upload-row">
            <el-input
              v-model="addForm.commodityAvatar"
              placeholder="请输入图片 URL 或上传本地封面"
            />
            <el-upload
              :http-request="handleCommodityAvatarUpload"
              :show-file-list="false"
              accept="image/*"
            >
              <el-button type="primary">上传封面</el-button>
            </el-upload>
          </div>
          <el-image
            v-if="addForm.commodityAvatar"
            :src="addForm.commodityAvatar"
            class="preview-image"
            :preview-src-list="[addForm.commodityAvatar]"
          />
        </el-form-item>
        <el-form-item label="新旧程度" prop="degree">
          <el-input v-model="addForm.degree" placeholder="例如：九成新" />
        </el-form-item>
        <el-form-item label="商品分类" prop="commodityTypeId">
          <el-select
            v-model="addForm.commodityTypeId"
            placeholder="请选择商品分类"
          >
            <el-option
              v-for="type in commodityTypeList"
              :key="type.id"
              :label="type.typeName"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input v-model="addForm.price" placeholder="请输入价格" />
        </el-form-item>
        <el-form-item label="商品库存" prop="commodityInventory">
          <el-input-number
            v-model="addForm.commodityInventory"
            :min="1"
            :step="1"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddCommodity">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  addCommodityUsingPost,
  listCommodityVoByPageUsingPost
} from "@/api/commodityController";
import { uploadFileUsingPost } from "@/api/fileController";
import { listCommodityTypeVoByPageUsingPost } from "@/api/commodityTypeController";
import CommodityList from "@/components/CommodityList/index.vue";
import { ElMessage } from "element-plus";
import { animateIn } from "@/utils/motion";

const pageRef = ref<HTMLElement | null>(null);
const commodityList = ref([]);
const total = ref(0);
const pageSize = ref(10);
const currentPage = ref(1);
const commodityTypeList = ref([]);

const queryParams = ref({
  commodityName: "",
  commodityDescription: "",
  degree: "",
  commodityInventory: "",
  commodityTypeId: ""
});

const getCommodityList = async () => {
  try {
    const res = await listCommodityVoByPageUsingPost({
      current: currentPage.value,
      pageSize: pageSize.value,
      commodityName: queryParams.value.commodityName,
      commodityDescription: queryParams.value.commodityDescription,
      degree: queryParams.value.degree,
      commodityInventory: queryParams.value.commodityInventory,
      commodityTypeId: queryParams.value.commodityTypeId,
      isListed: 1
    });
    if (res.code === 200) {
      commodityList.value = res.data.records;
      total.value = parseInt(res.data.total);
    } else {
      ElMessage.error("获取商品列表失败");
    }
  } catch (error: any) {
    ElMessage.error("获取商品列表失败", error);
  }
};

const getCommodityTypeList = async () => {
  try {
    const res = await listCommodityTypeVoByPageUsingPost({
      pageSize: 1000,
      current: 1
    });
    if (res.code === 200) {
      commodityTypeList.value = res.data.records;
    } else {
      ElMessage.error("获取商品分类列表失败");
    }
  } catch (error: any) {
    ElMessage.error("获取商品分类列表失败", error);
  }
};

const resetQuery = () => {
  queryParams.value = {
    commodityName: "",
    commodityDescription: "",
    degree: "",
    commodityInventory: "",
    commodityTypeId: ""
  };
  getCommodityList();
};

const handlePageChange = (page: number) => {
  currentPage.value = page;
  getCommodityList();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  getCommodityList();
};

onMounted(() => {
  getCommodityList();
  getCommodityTypeList();
  animateIn(
    pageRef.value?.querySelectorAll(
      ".market-page-header, .market-filter-card"
    ) || []
  );
});

const addDialogVisible = ref(false);

const addForm = ref({
  commodityName: "",
  commodityDescription: "",
  degree: "",
  commodityTypeId: "",
  price: 0,
  commodityAvatar: "",
  commodityInventory: 1
});

const handleAddCommodity = async () => {
  try {
    const res = await addCommodityUsingPost(addForm.value);
    if (res.code === 200) {
      ElMessage.success("发布成功");
      addDialogVisible.value = false;
      resetAddForm();
      await getCommodityList();
    } else {
      ElMessage.error("发布失败");
    }
  } catch (error) {
    ElMessage.error("发布失败");
  }
};

const handleCommodityAvatarUpload = async (options: any) => {
  try {
    const res = await uploadFileUsingPost(
      { biz: "commodity_avatar" },
      {},
      options.file
    );
    if (res.code !== 200) {
      return ElMessage.error("上传封面失败");
    }
    addForm.value.commodityAvatar = res.data || "";
    ElMessage.success("上传封面成功");
  } catch (error) {
    ElMessage.error("上传封面失败");
  }
};

const resetAddForm = () => {
  addForm.value = {
    commodityName: "",
    commodityDescription: "",
    degree: "",
    commodityTypeId: "",
    price: 0,
    commodityAvatar: "",
    commodityInventory: 1
  };
};
</script>

<style scoped lang="scss">
.commodity-page {
  display: block;
}

.upload-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.preview-image {
  width: 150px;
  height: 150px;
  margin-top: 12px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  object-fit: cover;
}

@media (max-width: 560px) {
  .upload-row {
    grid-template-columns: 1fr;
  }
}
</style>
