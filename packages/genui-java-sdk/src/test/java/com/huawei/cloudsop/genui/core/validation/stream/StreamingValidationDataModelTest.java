package com.huawei.cloudsop.genui.core.validation.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMetadata;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class StreamingValidationDataModelTest {

    @Test
    void everyStreamingProbeReceivesTheRenderDataModel() {
        Map<String, Object> dataModel = new LinkedHashMap<>();
        dataModel.put("total", 3);
        dataModel.put("optional", null);
        AtomicReference<ValidationRequest> captured = new AtomicReference<>();
        OpenuiLangValidator validator = request -> {
            captured.set(request);
            return ValidationResult.valid(request.dsl(), List.of(),
                    new ValidationMetadata(1, "root", request.mode(), null));
        };
        StreamingValidationSession session = new StreamingValidationSession(validator,
                GenerationContractLoader.loadDefault(), "Stack", dataModel);

        session.onDelta("root = Stack([])\n");
        session.onEnd();

        assertEquals(dataModel, captured.get().dataModel());
    }
}
