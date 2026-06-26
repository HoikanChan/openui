package com.huawei.cloudsop.genui.core;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.contract.ToolAnnotations;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.prompt.PromptAssembler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BaseContractFixtureTest {
  @Test
  void loadsGeneratedDslEngineBaseContractResource() {
    GenerationContract contract = GenerationContractLoader.loadDefault();

    assertEquals("@cloudsop/openui-react-ui-dsl@0.1.0", contract.contractVersion());
    assertEquals("Stack", contract.root());
    assertTrue(contract.components().containsKey("Stack"));
    assertFalse(contract.components().get("Stack").propsSchema().isEmpty());
    assertTrue(
        contract.componentGroups().stream()
            .anyMatch(group -> group.name().equals("Layout") && group.components().contains("Stack")));
    assertFalse(contract.additionalRules().isEmpty());
    assertFalse(contract.examples().isEmpty());
  }
}
