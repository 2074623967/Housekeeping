<template>
  <AppRefundProgressView :terminal-variant="terminalVariant" />
</template>

<script setup>
import AppRefundProgressView from "../../../app-web/src/views/RefundProgressView.vue";

defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});
</script>
