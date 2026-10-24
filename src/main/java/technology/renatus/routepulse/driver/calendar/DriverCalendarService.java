package technology.renatus.routepulse.driver.calendar;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Network requests happen outside database transactions and organization locks. */
@Service
public class DriverCalendarService {
    private final DriverCalendarStore store;private final UkgCalendarClient client;private final CalendarLinkCipher cipher;
    public DriverCalendarService(DriverCalendarStore store,UkgCalendarClient client,CalendarLinkCipher cipher) {
        this.store=store;this.client=client;this.cipher=cipher;
    }
    public void connect(Authentication auth,Long id,String input) {
        store.authorize(auth,id);String link=client.normalize(input);var shifts=client.fetch(link);
        store.connect(auth,id,link,shifts,Instant.now());
    }
    public void refresh(Authentication auth,Long id) { refresh(store.snapshot(auth,id)); }
    private void refresh(DriverCalendarStore.Snapshot snapshot) {
        java.util.List<UkgCalendarParser.Shift> parsed=null;String error=null;
        try {
            parsed=client.fetch(cipher.decrypt(snapshot.org(),snapshot.driver(),snapshot.encryptedLink()));
        } catch(IllegalArgumentException|IllegalStateException exception) {
            error=exception.getMessage();
        }
        store.apply(snapshot,parsed,error,Instant.now());
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void refreshDue() { for(var snapshot:store.due(Instant.now())) refresh(snapshot); }
}
