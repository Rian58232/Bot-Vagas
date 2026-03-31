package com.botvagas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "botvagas.whatsapp.auto-start=false",
        "botvagas.scheduler.executar-ao-iniciar=false"
})
class BotVagasApplicationTests {

    @Test
    void contextLoads() {
    }
}
