package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;

import org.junit.jupiter.api.Test;

class BuiltinTypeRegistryTest {

    @Test
    void registryCoversEveryContractBuiltin() {
        // 词表恒从默认契约派生——类型求值器与解析器词表是同一来源，无平行集合可对账。
        assertEquals(GenerationContractLoader.loadDefault().builtins().size(),
                new BuiltinTypeRegistry(BuiltinCatalog.getDefault().specsForTypeValidation()).names().size());
        assertEquals(BuiltinCatalog.getDefault().names(),
                new BuiltinTypeRegistry(BuiltinCatalog.getDefault().specsForTypeValidation()).names());
    }
}
