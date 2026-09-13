package cgeo.geocaching.connector.pgc;

import androidx.test.filters.MediumTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ProjectGCAPITest {

    @Test
    @MediumTest
    public void testPing() {
        final JsonNode result = ProjectGCAPI.get("/ping");
        assertThat(result.asText().equals("pong"));
    }

    @Test
    @MediumTest
    public void testUser() {
        final JsonNode result = ProjectGCAPI.get("/user");
        System.out.println("ProjectGCAPI /user: " + result);
    }

    @Test
    @MediumTest
    public void testPgcPlus() {
        final JsonNode result = ProjectGCAPI.get("/test/pgc-plus");
        System.out.println("ProjectGCAPI /test/pgc-plus: " + result);
    }
}
