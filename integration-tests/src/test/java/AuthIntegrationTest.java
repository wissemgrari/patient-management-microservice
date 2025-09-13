import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// 1. Arrange
// 2. Act
// 3. Assert

public class AuthIntegrationTest {

  @BeforeAll
  static void setUp() {
    RestAssured.baseURI = "http://localhost:4004";
  }

  @Test
  public void shouldReturnOkWithValidToken() {
    String loginPayload = """
        {
          "email": "testuser@test.com",
          "password": "password123"
        }
        """;

    Response response = given()
        .contentType(ContentType.JSON)
        .body(loginPayload)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("token", notNullValue())
        .extract()
        .response();

    System.out.println("Generated Token: " + response.jsonPath().getString("token"));
  }

  @Test
  public void shouldReturnUnauthorizedOnInvalidLogin() {
    String loginPayload = """
        {
          "email": "wrong_user@test.com",
          "password": "wrong_password"
        }
        """;

    given()
        .contentType(ContentType.JSON)
        .body(loginPayload)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(HttpStatus.SC_UNAUTHORIZED);
  }

}
