<template>
  <div class="calendar-chart-shell">
    <div ref="chartDom" class="calendar-chart"></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import * as echarts from "echarts";

const props = defineProps<{
  data: { date: string; value: number }[];
  year: string;
}>();

const chartDom = ref<HTMLElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let renderTimer: number | null = null;

const getChartWidth = () => chartDom.value?.clientWidth || 0;

const renderChart = async () => {
  await nextTick();
  if (!chartDom.value) {
    return;
  }

  const chartWidth = getChartWidth();
  if (chartWidth < 300) {
    return;
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartDom.value);
  }

  const eChartsData = props.data.map((item) => [item.date, item.value]);
  const maxValue = Math.max(1, ...props.data.map((item) => item.value || 0));
  const cellWidth = Math.max(
    12,
    Math.min(18, Math.floor((chartWidth - 130) / 54))
  );

  chartInstance.setOption(
    {
      tooltip: {
        formatter: (params: any) => {
          const date = params.data?.[0] || "";
          const value = params.data?.[1] || 0;
          return `日期: ${date}<br>订单数量: ${value}`;
        }
      },
      visualMap: {
        show: false,
        min: 0,
        max: maxValue,
        inRange: {
          color: ["#f6f8fb", "#b7e9bf", "#42a35a"]
        }
      },
      calendar: {
        orient: "horizontal",
        range: props.year,
        cellSize: [cellWidth, 18],
        left: 80,
        right: 24,
        top: 74,
        bottom: 34,
        yearLabel: {
          show: true,
          position: "top",
          margin: 18,
          color: "#5f6773",
          fontSize: 18,
          fontWeight: 600
        },
        dayLabel: {
          firstDay: 1,
          nameMap: "ZH",
          margin: 10,
          color: "#5f6773"
        },
        monthLabel: {
          nameMap: "ZH",
          margin: 12,
          color: "#5f6773"
        },
        itemStyle: {
          borderColor: "#d9e0ea",
          borderWidth: 1
        }
      },
      series: [
        {
          type: "heatmap",
          coordinateSystem: "calendar",
          data: eChartsData
        }
      ]
    },
    true
  );

  chartInstance.resize({ width: chartWidth });
};

const scheduleRender = () => {
  if (renderTimer !== null) {
    window.cancelAnimationFrame(renderTimer);
  }
  renderTimer = window.requestAnimationFrame(() => {
    renderTimer = null;
    renderChart();
  });
};

onMounted(() => {
  if (chartDom.value) {
    resizeObserver = new ResizeObserver(() => {
      scheduleRender();
      chartInstance?.resize();
    });
    resizeObserver.observe(chartDom.value);
  }
  scheduleRender();
});

onUnmounted(() => {
  if (renderTimer !== null) {
    window.cancelAnimationFrame(renderTimer);
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
  chartInstance?.dispose();
  chartInstance = null;
});

watch(
  () => [props.data, props.year],
  () => {
    scheduleRender();
  },
  { deep: true }
);
</script>

<style scoped>
.calendar-chart-shell {
  width: 100%;
  overflow-x: auto;
  padding: 12px 0 4px;
}

.calendar-chart {
  width: 100%;
  min-width: 860px;
  height: 280px;
}
</style>
