package cc.openxiot.device.api.accesspoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class ReplicaResourceTest {
    @Test
    void testInstanceIdEndpoint() {
        given()
          .when().get("/v1/replica/id")
          .then()
             .statusCode(200)
             .body("success", is(true))
             .body("data", is(notNullValue()));
    }

    @Test
    void testIpEndpoint() {
        given()
          .when().get("/v1/replica/ip")
          .then()
             .statusCode(200)
             .body("success", is(true))
             .body("data", is(notNullValue()));
    }
}
