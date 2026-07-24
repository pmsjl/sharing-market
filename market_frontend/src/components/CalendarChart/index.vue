<template>
  <div class="calendar-chart-shell">
    <div class="calendar-heading">
      <div>
        <span>SHOPPING CALENDAR</span>
        <strong>{{ year }} 年购物印记</strong>
      </div>
      <small>颜色越深，成交越活跃</small>
    </div>
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

  const today = new Date().toISOString().slice(0, 10);
  const eChartsData = props.data.map((item) => ({
    value: [item.date, item.value],
    itemStyle:
      item.date === today
        ? {
            borderColor: "#e0651f",
            borderWidth: 2
          }
        : undefined
  }));
  const maxValue = Math.max(1, ...props.data.map((item) => item.value || 0));
  const cellWidth = Math.max(
    12,
    Math.min(18, Math.floor((chartWidth - 130) / 54))
  );

  chartInstance.setOption(
    {
      tooltip: {
        borderWidth: 1,
        borderColor: "#d7b98c",
        backgroundColor: "rgba(35, 49, 63, 0.94)",
        textStyle: {
          color: "#fdf6e3",
          fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif'
        },
        extraCssText:
          "border-radius:8px;box-shadow:0 12px 26px rgba(35,49,63,.2);",
        formatter: (params: any) => {
          const date = params.data?.value?.[0] || "";
          const value = params.data?.value?.[1] || 0;
          return `<strong>${date}</strong><br/>成交印记：${value} 次`;
        }
      },
      visualMap: {
        show: false,
        min: 0,
        max: maxValue,
        inRange: {
          color: ["#f7ecd8", "#f2b8a0", "#e0651f", "#2b6e50"]
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
          borderColor: "rgba(35, 49, 63, 0.1)",
          borderWidth: 1
        }
      },
      series: [
        {
          type: "heatmap",
          coordinateSystem: "calendar",
          data: eChartsData,
          itemStyle: {
            borderRadius: 8,
            opacity: 0.9
          },
          emphasis: {
            itemStyle: {
              borderColor: "#e0651f",
              borderWidth: 2,
              shadowBlur: 8,
              shadowColor: "rgba(224, 101, 31, 0.32)"
            }
          }
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

<style scoped lang="scss">
.calendar-chart-shell {
  width: 100%;
  overflow-x: auto;
  padding: 20px;
  border-top: 8px solid transparent;
  background: linear-gradient(var(--market-surface), var(--market-surface))
      padding-box,
    repeating-linear-gradient(
        -45deg,
        var(--market-stamp-red),
        var(--market-stamp-red) 12px,
        var(--market-chalk) 12px,
        var(--market-chalk) 24px
      )
      border-box;
}

.calendar-heading {
  position: sticky;
  left: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  min-width: 620px;
  padding: 0 8px 12px;
  border-bottom: 1px dashed var(--market-line);

  div {
    display: grid;
    gap: 5px;
  }

  span,
  small {
    color: var(--market-muted);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 1.5px;
  }

  strong {
    font-family: var(--market-font-display);
    font-size: 22px;
  }
}

.calendar-chart {
  width: 100%;
  min-width: 860px;
  height: 280px;
}

@media (max-width: 600px) {
  .calendar-chart-shell {
    padding: 14px 10px;
  }
}
</style>
