export function replayActionMessage(task = {}) {
  if (task.taskStatus === "REPLAY_FAILED") {
    return {
      tone: "error",
      message: `任务 ${task.taskNo || ""} 重放失败，请排查消息代理与消费者后重新复核`
    };
  }
  if (task.taskStatus === "REPLAYING") {
    return {
      tone: "warning",
      message: `任务 ${task.taskNo || ""} 已进入重放状态，请刷新后继续核对`
    };
  }
  if (task.taskStatus === "REPLAYED") {
    return {
      tone: "success",
      message: `任务 ${task.taskNo || ""} 已完成消息发布确认，请继续核对目标系统`
    };
  }
  return {
    tone: "warning",
    message: `任务 ${task.taskNo || ""} 状态为 ${task.taskStatus || "未知"}，请刷新后确认`
  };
}
