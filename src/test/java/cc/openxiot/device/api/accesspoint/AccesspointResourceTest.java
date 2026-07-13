package cc.openxiot.device.api.accesspoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class AccesspointResourceTest {
    @Test
    void testPrivateHello() {
        given()
          .when().get("/v1/private/hello")
          .then()
             .statusCode(200)
             .body("success", is(true))
             .body("data", is("Hello from AccesspointResource"));
    }
}
