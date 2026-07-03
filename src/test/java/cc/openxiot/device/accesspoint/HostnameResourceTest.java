package cc.openxiot.device.accesspoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class HostnameResourceTest {
    @Test
    void testHostnameEndpoint() {
        given()
          .when().get("/v1/hostname")
          .then()
             .statusCode(200)
             .body("success", is(true))
             .body("data", is(notNullValue()));
    }
}
