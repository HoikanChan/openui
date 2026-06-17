# Context: OpenUI Domain Glossary

## Core Concepts

### LUI (Language UI)
The conversational dialog area where LLM responses are rendered. Default rendering target for DSL components.

### Canvas (Intelligent Canvas)
A multi-tab visualization workspace independent from LUI dialog. Supports canvas cards, preview tabs, and embedded HTML content.

### CanvasTab
The single dashboard tab within Canvas that displays CanvasCard components in a 12-column grid layout.

### PreviewTab
A tab within Canvas that renders PreviewCard content in full-page mode. Each PreviewTab has a tabId and holds multiple PreviewCardData items (cards array). PreviewTabs are created or targeted by tabId.

### HTMLLoader Tab
A PreviewTab that embeds external HTML content via iframe with bidirectional postMessage communication. iframeId serves as the communication identifier.

## DSL Components

### CanvasCard
A DSL component that renders to CanvasTab in Canvas. Displays as a grid card with optional title, cardId (deduplication key), and size constraints. Same cardId replaces existing card.

### PreviewCard
A DSL component that renders to a PreviewTab in Canvas. Title defines the tab name, content fills the entire tab area. Supports cardId (dedup within tab), tabId (target specific tab), and type ("replace" | "append"). HTMLLoader can be used as PreviewCard children for iframe embedding.

### HTMLLoader
A DSL component for iframe embedding with bidirectional communication. Used as PreviewCard children. Args: url (required, iframe source), iframeId (required, communication identifier), data (optional, object sent to iframe after ready signal). Communication protocol: iframe sends `{type: "openui-ready", iframeId}` when loaded → HTMLLoader sends `{type: "openui-data", iframeId, data}` → iframe can send `{type: "openui-close", iframeId}` to request tab removal.

## Rendering Targets

### LUI Target
Default rendering destination. Components without canvas-specific markers render to the LUI dialog area.

### Canvas Target
Rendering destination for CanvasCard, PreviewCard, and HTMLLoader components. These components push data to canvasStore and render in Canvas instead.

## Layout Concepts

### Grid Layout
12-column fixed grid used by CanvasTab. CanvasCards arranged left-to-right, wrapping to next row when space insufficient.

### Full-Page Layout
Layout mode for PreviewTab where content occupies entire tab area without grid constraints.

### Grid Unit
Size measurement unit for CanvasCard. `{w: number}` represents column span (1-12). Default width is 6 columns.

## Store Model

### canvasStore
Unified store with subscribe/getSnapshot pattern. State: `{ canvasCards: CanvasCardData[], previewTabs: PreviewTabData[] }`. hasData is a getter.

- `addCanvasCard(card, cardId?)` — dedup by cardId (replace if match, append if absent)
- `addPreviewCard(card, tabId?, type?)` — card-level dedup within tab by cardId
- `removeCanvasCard(cardId)` — remove from flat canvasCards array
- `removePreviewCard(tabId, cardId)` — remove from specific tab's cards array
- `removePreviewTab(tabId)` — remove entire preview tab
- `clear()` — reset all state

## ElementNode

Parsed representation of a DSL component invocation. Contains `typeName`, `props`, `statementId`, and `partial` flag.

### CanvasItem
Union type of canvas-renderable ElementNodes: `CanvasCardNode | PreviewCardNode | HTMLLoaderNode`. Distinguished by `typeName` field.
