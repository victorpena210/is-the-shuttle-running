package technology.renatus.routepulse.auth;

import org.junit.jupiter.api.Test;

class LoginRoutingTests {
    @Test void savedRequestsRespectDriverRouting() throws Exception { LoginRoutingChecks.verify(); }
}
