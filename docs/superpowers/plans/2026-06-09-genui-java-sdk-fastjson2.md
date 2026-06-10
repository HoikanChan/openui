# GenUI Java SDK Fastjson2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Java SDK's custom JSON implementation with a Fastjson2-backed adapter while keeping every prompt golden output byte-for-byte unchanged.

**Architecture:** `Json` remains the package-private policy boundary. Fastjson2 owns parsing, compact serialization, escaping, and scalar number serialization; the adapter normalizes parsed/runtime values and retains only a small map/list pretty formatter to reproduce JavaScript's two-space and `": "` whitespace.

**Tech Stack:** Java 21, Maven 3.9.16, Fastjson2 2.0.61, JUnit 5.11.4

---

## File Structure

- Modify `packages/genui-java-sdk/pom.xml`: declare the Fastjson2 runtime dependency.
- Modify `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/JsonTest.java`: lock down the compatibility contract.
- Modify `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/Json.java`: replace the parser and serializers with the adapter.
- Modify `packages/genui-java-sdk/README.md`: make Maven the documented test path.
- Delete `packages/genui-java-sdk/run-tests.sh`: remove the duplicate dependency-resolution path.

### Task 1: Lock Down JSON Compatibility

**Files:**
- Modify: `packages/genui-java-sdk/pom.xml`
- Modify: `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/JsonTest.java`

- [ ] **Step 1: Add Fastjson2 to Maven**

Add:

```xml
<fastjson2.version>2.0.61</fastjson2.version>
```

and:

```xml
<dependency>
  <groupId>com.alibaba.fastjson2</groupId>
  <artifactId>fastjson2</artifactId>
  <version>${fastjson2.version}</version>
</dependency>
```

- [ ] **Step 2: Add compatibility tests**

Add tests that assert:

```java
Object parsed = Json.parse(
    "{\"first\":1,\"second\":1.5,\"third\":1e3,\"items\":[2]}");
Map<String, Object> object = Json.asObject(parsed, "value");

assertEquals(List.of("first", "second", "third", "items"),
    new ArrayList<>(object.keySet()));
assertInstanceOf(LinkedHashMap.class, object);
assertInstanceOf(Long.class, object.get("first"));
assertInstanceOf(Double.class, object.get("second"));
assertInstanceOf(Double.class, object.get("third"));
assertInstanceOf(ArrayList.class, object.get("items"));
```

Also assert exact compact and pretty output:

```java
assertEquals(
    "{\"value\":7,\"nested\":[{\"enabled\":true}]}",
    Json.stringify(value));
assertEquals(
    """
    {
      "value": 7,
      "nested": [
        {
          "enabled": true
        }
      ]
    }""",
    Json.stringifyPretty(value));
```

Finally assert malformed JSON stays behind the SDK exception boundary and
retains the vendor failure as its cause:

```java
GenerationSdkException error =
    assertThrows(GenerationSdkException.class, () -> Json.parse("{"));
assertInstanceOf(com.alibaba.fastjson2.JSONException.class, error.getCause());
```

- [ ] **Step 3: Run the focused test and verify the new boundary test fails**

Run:

```bash
mvn -Dtest=JsonTest test
```

Expected: the malformed JSON test fails because the current custom parser does
not wrap a Fastjson2 exception.

- [ ] **Step 4: Commit the dependency and tests**

```bash
git add packages/genui-java-sdk/pom.xml \
  packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/JsonTest.java
git commit -m "test(genui-java-sdk): lock json compatibility"
```

### Task 2: Replace The Custom JSON Implementation

**Files:**
- Modify: `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/Json.java`

- [ ] **Step 1: Replace parsing with Fastjson2 and normalization**

Implement `parse` using `JSON.parse`, catch `JSONException`, and recursively
normalize:

```java
static Object parse(String json) {
  try {
    return normalizeParsed(JSON.parse(json));
  } catch (JSONException error) {
    throw new GenerationSdkException("Invalid JSON: " + error.getMessage(), error);
  }
}
```

Normalization returns `LinkedHashMap<String, Object>`, `ArrayList<Object>`,
`Long` for integral values, and `Double` for decimal/exponent values. Values
outside the `Long` range throw `GenerationSdkException`.

- [ ] **Step 2: Replace compact serialization and scalar handling**

`stringify` calls:

```java
return JSON.toJSONString(normalizeForSerialization(value));
```

`normalizeForSerialization` recursively preserves map order and iterable order,
converts non-finite floats to `null`, converts finite `Float` to `Double`, and
converts integral floating-point values with absolute value below `1e15` to
`Long`. Unsupported values become `String.valueOf(value)`, matching the current
adapter.

- [ ] **Step 3: Implement structure-only pretty formatting**

Walk normalized maps and lists to write braces, commas, newlines, two-space
indentation, and `": "`. Serialize every key and scalar through
`JSON.toJSONString` so escaping and scalar JSON semantics stay owned by
Fastjson2.

- [ ] **Step 4: Run focused adapter tests**

Run:

```bash
mvn -Dtest=JsonTest test
```

Expected: all `JsonTest` tests pass.

- [ ] **Step 5: Run prompt golden tests**

Run:

```bash
mvn -Dtest=PromptGoldenTest test
```

Expected: 8 tests pass with no prompt drift.

- [ ] **Step 6: Commit the adapter**

```bash
git add packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/Json.java
git commit -m "refactor(genui-java-sdk): use fastjson2"
```

### Task 3: Make Maven The Only Test Path

**Files:**
- Modify: `packages/genui-java-sdk/README.md`
- Delete: `packages/genui-java-sdk/run-tests.sh`

- [ ] **Step 1: Replace script documentation**

Document:

```bash
cd packages/genui-java-sdk
mvn test
mvn -Dtest=PromptGoldenTest test
```

Remove claims that Maven is unavailable or that the console launcher is
downloaded manually.

- [ ] **Step 2: Delete the obsolete runner**

Delete `packages/genui-java-sdk/run-tests.sh`.

- [ ] **Step 3: Run the full Maven suite**

Run:

```bash
mvn test
```

Expected: all existing 22 tests plus the new adapter tests pass.

- [ ] **Step 4: Commit build documentation**

```bash
git add packages/genui-java-sdk/README.md packages/genui-java-sdk/run-tests.sh
git commit -m "docs(genui-java-sdk): use maven test workflow"
```

### Task 4: Verify Golden Integrity And Publish

**Files:**
- Verify: `packages/genui-java-sdk/src/test/resources/prompt-golden/*`

- [ ] **Step 1: Verify golden fixtures have no diff**

Run:

```bash
git diff --exit-code HEAD -- packages/genui-java-sdk/src/test/resources/prompt-golden
```

Expected: exit code 0 and no output.

- [ ] **Step 2: Verify the final diff and repository state**

Run:

```bash
git diff --check
git status --short
git log -5 --oneline
```

Expected: no whitespace errors; only pre-existing unrelated user changes remain
uncommitted.

- [ ] **Step 3: Push the existing PR branch**

Run:

```bash
git push
```

Expected: branch `genui-java-sdk-prompt-assembly` updates the existing PR.
