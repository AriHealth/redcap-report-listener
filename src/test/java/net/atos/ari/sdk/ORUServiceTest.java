package net.atos.ari.sdk;

import org.hl7.fhir.dstu3.model.Patient;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import net.atos.ari.sdk.client.Client;
import net.atos.ari.sdk.oru.ORUR01;

@RunWith(SpringRunner.class)
public class ORUServiceTest {
    
    @MockBean
    Client client;
    
    @Test
    @Ignore
    public void givenORU_whenServiceStarts_thenReturnOK() {
        ORUR01 oruObject = client.createORU(new Patient(), null, null, "");
    }
}