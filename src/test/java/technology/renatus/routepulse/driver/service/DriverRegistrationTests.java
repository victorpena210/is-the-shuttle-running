package technology.renatus.routepulse.driver.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;
class DriverRegistrationTests {
    @Test void nameAndEmailRegisterWithoutAnySchedule() {
        var drivers = mock(DriverRepository.class); var orgs = mock(OrganizationRepository.class);
        var shifts = mock(UploadedShiftRepository.class); var org = mock(Organization.class);
        when(orgs.findById(1L)).thenReturn(Optional.of(org)); when(drivers.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = new DriverService(drivers, orgs, shifts).registerDriver(1L,"100"," Victor Peña ","Victor@example.com",true);
        assertThat(result.getName()).isEqualTo("Victor Peña"); assertThat(result.getEmail()).isEqualTo("victor@example.com");
        verifyNoInteractions(shifts);
    }
    @Test void blankNameIsRejected() {
        var drivers = mock(DriverRepository.class); var orgs = mock(OrganizationRepository.class); var shifts = mock(UploadedShiftRepository.class);
        assertThatThrownBy(() -> new DriverService(drivers,orgs,shifts).registerDriver(1L,"100"," ","v@example.com",true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        verify(drivers,never()).save(any()); verifyNoInteractions(shifts);
    }
}
