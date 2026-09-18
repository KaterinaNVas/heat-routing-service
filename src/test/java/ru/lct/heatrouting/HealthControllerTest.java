package ru.lct.heatrouting;

import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.api.HealthController;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {
    @Test
    void healthEndpointReturnsUp() {
        HealthController controller = new HealthController();
        assertEquals("UP", controller.health().get("status"));
        assertEquals("heat-routing-service", controller.health().get("service"));
    }
}
