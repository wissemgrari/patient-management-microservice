import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PatientIntegrationTest {

  @BeforeAll
  static void setup() {
    RestAssured.baseURI = "http://localhost:4004";
  }

  @Test
  public void shouldReturnPatientWithValidToken() {
    String loginPayload = """
        {
          "email": "testuser@test.com",
          "password": "password123"
        }
        """;

    String token = given()
        .contentType(ContentType.JSON)
        .body(loginPayload)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .jsonPath()
        .get("token");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/api/patients")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("patients", notNullValue());
  }

}
