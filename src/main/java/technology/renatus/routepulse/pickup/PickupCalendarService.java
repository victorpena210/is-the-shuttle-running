package technology.renatus.routepulse.pickup;

import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.driver.calendar.*;

/** Fetch remotely outside transactions; apply only to the same connection revision. */
@Service
public class PickupCalendarService {
    private final PickupStore store;private final UkgCalendarClient client;private final CalendarLinkCipher cipher;
    public PickupCalendarService(PickupStore store,UkgCalendarClient client,CalendarLinkCipher cipher){this.store=store;this.client=client;this.cipher=cipher;}
    public void connect(Authentication auth,Long id,String name,String location,String input) {
        store.authorizeWrite(auth,id);String link=client.normalize(input);var parsed=client.fetchDetails(link);
        store.connect(auth,id,name,location,link,parsed,Instant.now());
    }
    public boolean refresh(Authentication auth,Long id){return refresh(store.snapshot(auth,id));}
    private boolean refresh(PickupStore.Snapshot snapshot) {
        List<UkgCalendarParser.DetailedShift> parsed=null;
        try {parsed=client.fetchDetails(cipher.decrypt(snapshot.org(),-snapshot.id(),snapshot.encryptedLink()));}
        catch(IllegalArgumentException|IllegalStateException ignored) { /* Never log private subscription links. */ }
        store.apply(snapshot,parsed,Instant.now());return parsed!=null;
    }
    @Scheduled(fixedDelay=60000,initialDelay=90000)
    public void refreshDue(){for(var snapshot:store.due(Instant.now()))refresh(snapshot);}
}
