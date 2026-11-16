package vn.edu.fpt.savora.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApiResponseTest {
    @Test void successfulResponse() {
        ApiResponse<String> r=ApiResponse.ok("Saved", "ok");
        assertTrue(r.isSuccess());
        assertEquals("Saved",r.getMessage());
        assertEquals("ok",r.getData());
        assertNotNull(r.getTimestamp());
    }
    @Test void errorResponse() {
        ApiResponse<Void> r=ApiResponse.error("BAD","Invalid");
        assertFalse(r.isSuccess());
        assertEquals("BAD",r.getErrorCode());
    }
}
