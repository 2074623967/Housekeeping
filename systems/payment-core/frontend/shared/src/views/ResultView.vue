<template>
  <AppResultView :terminal-variant="terminalVariant" />
</template>

<script setup>
import AppResultView from "../../../app-web/src/views/ResultView.vue";

defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});
</script>
