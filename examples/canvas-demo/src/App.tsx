import { useState, useCallback, useMemo } from "react";
import { Renderer, createParser } from "@openuidev/react-lang";
import { CanvasTabs, dslLibrary, canvasStore } from "@openuidev/react-ui-dsl";
import { ConfigProvider } from "antd";

const EXAMPLE_DSL = `
root = Stack([text, preview1])
text = TextContent("Hello from LUI")
preview1 = PreviewCard([cardText], "My Preview")
cardText = TextContent("Full preview content here")
`;

const DEMO_DSL_TABLE = `root = Stack([header, table])
header = TextContent("Device List", "large")
table = Table([nameCol, statusCol], data.devices)
nameCol = Col("Name", "name")
statusCol = Col("Status", "status")`;

const DEMO_DSL_CHART = `root = Stack([title, chart])
title = TextContent("Traffic Trend", "large")
chart = LineChart(["Mon", "Tue", "Wed"], [Series("Inbound", [120, 200, 150])], "smooth", "Time", "Mbps")`;

const DEMO_DSL_KPI = `root = Stack([kpiCard])
kpiCard = Card([cardTitle, miniChart])
cardTitle = TextContent("7-Day Latency", "large")
miniChart = MiniChart("line", [45, 52, 38, 60, 55, 48, 42])`;

const DEMO_DSL_DESC = `root = Descriptions([field1, field2, field3])
field1 = DescGroup("Server Info", [DescField("Host", "web-01.example.com"), DescField("IP", "10.0.1.42"), DescField("Status", data.devices[0].status)])
field2 = DescGroup("Network", [DescField("Port", "443"), DescField("Protocol", "HTTPS")])
field3 = DescGroup("Load", [DescField("CPU", "42%"), DescField("Memory", "68%")])`;

const EXAMPLE_DATA_MODEL = {
  devices: [
    { name: "Router-A", status: "Up" },
    { name: "Switch-B", status: "Down" },
    { name: "Firewall-C", status: "Warning" },
    { name: "Gateway-D", status: "Up" },
  ],
};

const parser = createParser(dslLibrary.toJSONSchema());

function parseDslToChildren(dsl: string): unknown[] | null {
  const result = parser.parse(dsl);
  if (result.root) {
    return [result.root];
  }
  return null;
}

function App() {
  const [dslInput, setDslInput] = useState(EXAMPLE_DSL);
  const [isStreaming, setIsStreaming] = useState(false);
  const [batchCount, setBatchCount] = useState(20);

  const dataModel = useMemo(() => EXAMPLE_DATA_MODEL, []);

  const handleAddPreviewCard = useCallback((dsl: string, title: string) => {
    const children = parseDslToChildren(dsl);
    if (children) {
      canvasStore.addPreviewTab({ title, children });
    }
  }, []);

  const handleAddDashboardCard = useCallback(
    (dsl: string, title: string, tab?: string, size?: { w?: number }) => {
      const children = parseDslToChildren(dsl);
      if (children) {
        canvasStore.addDashboardCard({ title, children, size }, tab);
      }
    },
    []
  );

  const handleAddHtmlLoader = useCallback(
    (url: string, iframeId: string, title: string) => {
      canvasStore.addPreviewTab({ title, children: [], url, iframeId });
    },
    []
  );

  const handleBatchAdd = useCallback(
    (dsl: string, type: string, count: number) => {
      for (let i = 0; i < count; i++) {
        const children = parseDslToChildren(dsl);
        if (children) {
          const w = type === "wide" ? 12 : 6;
          canvasStore.addDashboardCard(
            { title: `${type} #${i + 1}`, children, size: { w } },
            "StressTest"
          );
        }
      }
    },
    []
  );

  const handleClear = useCallback(() => {
    canvasStore.clear();
    setDslInput("");
  }, []);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: "#1890ff" } }}>
      <div className="app-container">
        <div className="lui-panel">
          <div className="panel-header">LUI Dialog</div>
          <div className="panel-content">
            <textarea
              className="dsl-input"
              value={dslInput}
              onChange={(e) => setDslInput(e.target.value)}
              placeholder="Enter OpenUI DSL code..."
              spellCheck={false}
            />

            <div className="button-row">
              <button className="button" onClick={() => setIsStreaming(false)}>
                Render
              </button>
              <button className="button button-secondary" onClick={() => setDslInput("")}>
                Clear DSL
              </button>
            </div>

            <div className="section-divider" />

            <div className="store-section">
              <div className="store-title">canvasStore Direct API</div>

              <div className="store-group">
                <span className="store-label">PreviewCard:</span>
                <button className="button-sm" onClick={() => handleAddPreviewCard(DEMO_DSL_TABLE, "Device List")}>
                  Table
                </button>
                <button className="button-sm" onClick={() => handleAddPreviewCard(DEMO_DSL_CHART, "Traffic Trend")}>
                  Chart
                </button>
                <button className="button-sm" onClick={() => handleAddPreviewCard(DEMO_DSL_KPI, "KPI Card")}>
                  KPI
                </button>
              </div>

              <div className="store-group">
                <span className="store-label">DashboardCard:</span>
                <button
                  className="button-sm"
                  onClick={() => handleAddDashboardCard(DEMO_DSL_TABLE, "Devices", "Dashboard", { w: 6 })}
                >
                  Table (w=6)
                </button>
                <button
                  className="button-sm"
                  onClick={() => handleAddDashboardCard(DEMO_DSL_CHART, "Traffic", "Dashboard", { w: 6 })}
                >
                  Chart (w=6)
                </button>
                <button
                  className="button-sm"
                  onClick={() => handleAddDashboardCard(DEMO_DSL_DESC, "Server Info", "Dashboard", { w: 6 })}
                >
                  Descriptions (w=6)
                </button>
                <button
                  className="button-sm"
                  onClick={() => handleAddDashboardCard(DEMO_DSL_TABLE, "Devices", "Network", { w: 12 })}
                >
                  Table (w=12)
                </button>
              </div>

              <div className="store-group">
                <span className="store-label">HTMLLoader:</span>
                <button
                  className="button-sm"
                  onClick={() => handleAddHtmlLoader("https://example.com", "html-demo-1", "External Page")}
                >
                  example.com
                </button>
              </div>

              <div className="section-divider" />

              <div className="store-title">Virtual Scroll Stress Test</div>

              <div className="store-group">
                <input
                  type="number"
                  value={batchCount}
                  onChange={(e) => setBatchCount(Number(e.target.value))}
                  min={1}
                  max={200}
                  style={{ width: 48, fontSize: 12, textAlign: "center" }}
                />
                <button
                  className="button-sm"
                  onClick={() => handleBatchAdd(DEMO_DSL_TABLE, "half", batchCount)}
                >
                  +{batchCount} Table (w=6)
                </button>
                <button
                  className="button-sm"
                  onClick={() => handleBatchAdd(DEMO_DSL_TABLE, "wide", batchCount)}
                >
                  +{batchCount} Table (w=12)
                </button>
                <button
                  className="button-sm"
                  onClick={() => handleBatchAdd(DEMO_DSL_CHART, "half", batchCount)}
                >
                  +{batchCount} Chart (w=6)
                </button>
                <button
                  className="button-sm"
                  onClick={() => handleBatchAdd(DEMO_DSL_DESC, "half", batchCount)}
                >
                  +{batchCount} Desc (w=6)
                </button>
              </div>
            </div>

            <div className="chat-messages" style={{ marginTop: 16 }}>
              <div className="message message-ai">
                <Renderer
                  response={dslInput}
                  library={dslLibrary}
                  dataModel={dataModel}
                  isStreaming={isStreaming}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="canvas-panel">
          <div className="panel-header">Intelligent Canvas</div>
          <div className="panel-content">
            <CanvasTabs
              library={dslLibrary}
              dataModel={dataModel}
              showClearButton={true}
              onClear={handleClear}
            />
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}

export default App;