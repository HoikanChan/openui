(function registerAlarmBusinessPiu() {
  const Prel = window.Prel;
  if (!Prel) {
    console.error("[alarm-business-piu] window.Prel is not available");
    return;
  }

  Prel.start("alarm-business-piu", "1.0.0", ["session", "locale"], function startAlarmPiu(socket, state) {
    const extension = window.__OPENUI_ALARM_RUNTIME_EXTENSION__;
    const dslEngine = window.DSL_ENGINE_PIU;

    if (!extension) {
      console.error("[alarm-business-piu] runtime extension is not available");
      return;
    }

    if (!dslEngine || typeof dslEngine.emit !== "function") {
      console.error("[alarm-business-piu] DSL engine Piu is not ready");
      return;
    }

    dslEngine.emit("smart-canvas:extend", extension);
  });
})();
