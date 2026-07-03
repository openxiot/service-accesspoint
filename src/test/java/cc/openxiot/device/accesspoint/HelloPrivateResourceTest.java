package cc.openxiot.device.accesspoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class HelloPrivateResourceTest {
    @Test
    void testPrivateHello() {
        given()
          .when().get("/v1/hello/private")
          .then()
             .statusCode(200)
             .body("success", is(true))
             .body("data", is("Hello from private"));
    }
}
