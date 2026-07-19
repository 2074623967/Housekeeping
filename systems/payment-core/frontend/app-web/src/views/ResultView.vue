<script setup>
import { useRoute } from "vue-router";
const route = useRoute();
</script>

<template>
  <div class="page">
    <div class="result">
      <h1>{{ route.params.status === "success" ? "支付成功" : "支付处理中" }}</h1>
      <p class="muted">支付结果页已生成，可继续返回业务页面或查看后台详情。</p>
    </div>
  </div>
</template>
