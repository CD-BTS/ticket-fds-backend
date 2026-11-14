package com.bts.fairticket.behavior;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.bts.fairticket.behavior.dto.*;
import static org.junit.jupiter.api.Assertions.*;

class AttemptMetadataTests {
    Map<String,Object> valid() {
        return new HashMap<>(Map.of("attemptId",UUID.randomUUID().toString(),"transitionId","start",
            "concertId","c1","stage","STARTED","status","ENTERED","sequence",1,
            "source","CLIENT_REPORT","recordedAt","2026-11-08T00:00:00Z"));
    }
    @Test void acceptsAttemptWithoutBookingAndRejectsMalformedIdentity() {
        var m=valid();
        var request=new EventRequest("service","external","user",EventType.BOOKING_ATTEMPT,null,null,"2026-11-08T00:00:00Z",m);
        assertTrue(request.isRequiredIdentifierValid()); assertTrue(request.isMetadataValid());
        m.put("sequence",1.5); assertFalse(request.isMetadataValid());
        m.put("sequence",2); assertFalse(request.isMetadataValid());
        m.put("sequence",1); m.put("attemptId","not-a-uuid"); assertFalse(request.isMetadataValid());
    }
    @Test void completionNeedsRealBookingAndOldBookingStillNeedsIdentifier() {
        var m=valid();m.put("stage","COMPLETED");m.put("status","SUCCEEDED");m.put("sequence",7);
        assertFalse(AttemptMetadata.valid(m));m.put("bookingId","b_1");assertTrue(AttemptMetadata.valid(m));
        assertFalse(new EventRequest("s","e","u",EventType.BOOKING,null,null,"2026-11-08T00:00:00Z",null).isRequiredIdentifierValid());
    }
}
