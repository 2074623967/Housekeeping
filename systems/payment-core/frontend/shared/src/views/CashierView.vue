<template>
  <AppCashierView :terminal-variant="terminalVariant" />
</template>

<script setup>
import AppCashierView from "../../../app-web/src/views/CashierView.vue";

defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});
</script>
