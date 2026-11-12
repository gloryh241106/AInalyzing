package vn.ainalyzing.api.llm;

import org.junit.jupiter.api.Test;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.Template.Group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentClassifierTest {

    @Test
    void intents() {
        assertEquals(Group.WHY_OPTION, IntentClassifier.intent("Nên nhập bao nhiêu? Vì sao chọn A?"));
        assertEquals(Group.WHY_OPTION, IntentClassifier.intent("vi sao chon B"));
        assertEquals(Group.WHAT_IF_PRICE, IntentClassifier.intent("Giá tối đa của B là bao nhiêu?"));
        assertEquals(Group.WHAT_IF_BUDGET, IntentClassifier.intent("Nếu ngân sách chỉ có 400.000đ thì sao?"));
        assertEquals(Group.WHAT_IF_SHELF_LIFE, IntentClassifier.intent("Hạn của lô B cần mấy ngày?"));
        assertEquals(Group.WHY_OPTION, IntentClassifier.intent("Ở kịch bản chậm thì B lỗ bao nhiêu?"));
        assertEquals(Group.UNSUPPORTED, IntentClassifier.intent("Viết cho tôi một bài thơ"));
        assertEquals(Group.UNSUPPORTED, IntentClassifier.intent(""));
    }

    @Test
    void giaiThichIsNotPrice() {
        assertEquals(Group.WHY_OPTION, IntentClassifier.intent("Giải thích giúp mình"));
    }

    @Test
    void scenarios() {
        assertEquals(Scenario.FAST, IntentClassifier.namedScenario("Nếu bán nhanh thì sao?"));
        assertEquals(Scenario.SLOW, IntentClassifier.namedScenario("kịch bản chậm"));
        assertEquals(Scenario.BASE, IntentClassifier.namedScenario("ở mức cơ sở"));
        assertNull(IntentClassifier.namedScenario("Vì sao chọn A?"));
        assertTrue(IntentClassifier.mentionsOtherScenario("Nếu bán nhanh thì sao?", Scenario.BASE));
        assertFalse(IntentClassifier.mentionsOtherScenario("Nếu bán nhanh thì sao?", Scenario.FAST));
        assertTrue(IntentClassifier.mentionsOtherScenario("So sánh các kịch bản", Scenario.BASE));
        assertFalse(IntentClassifier.mentionsOtherScenario("Vì sao chọn A?", Scenario.BASE));
    }
}
