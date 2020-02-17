/*
 * Copyright (C) 2020  Atos Spain SA. All rights reserved.
 * 
 * This file is part of the redcap-report-listener.
 * 
 * FHIRListenerService.java is free software: you can redistribute it and/or modify it under the 
 * terms of the Apache License, Version 2.0 (the License);
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * The software is provided "AS IS", without any warranty of any kind, express or implied,
 * including but not limited to the warranties of merchantability, fitness for a particular
 * purpose and noninfringement, in no event shall the authors or copyright holders be 
 * liable for any claim, damages or other liability, whether in action of contract, tort or
 * otherwise, arising from, out of or in connection with the software or the use or other
 * dealings in the software.
 * 
 * See README file for the full disclaimer information and LICENSE file for full license 
 * information in the project root.
 * 
 * @author Atos Research and Innovation, Atos SPAIN SA
 * 
 * Spring boot application for REDCap Report Listener
 */

package net.atos.ari.sdk;

import net.atos.ari.sdk.client.Client;
import net.atos.ari.sdk.oru.ACK;
import net.atos.ari.sdk.service.FHIRListenerService;
import net.atos.ari.sdk.service.REDCapListenerService;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Bundle.HTTPVerb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ws.WebServiceException;

@SpringBootApplication
public class REDCapListenerSpringApp {

    private static final Logger logger = LoggerFactory.getLogger(REDCapListenerSpringApp.class);


    /** Timer to wait until the next execution. */
    @Value("${redcap.listener.timer}")
    private Long timer;

    /** REDCap inclusion report instrument for PAPRIKA. */
    @Value("${redcap.paprika.instrument.report.inclusion}")
    private String instrumentInclusion;
    
    @Autowired
    private REDCapListenerService redcap;

    @Autowired
    private FHIRListenerService fhir;
    
    public static void main(String[] args) {
        SpringApplication.run(REDCapListenerSpringApp.class);
    }

    @Bean
    CommandLineRunner lookup(Client client) {
        return args -> {

            // Create a new REST client for FHIR
            fhir.configure();

            // while (true) {

                // Retrieve the records with fisioterapia_basal instrument complete and locked
                Map<String, Object> records = redcap.export();
                if (records != null)
                    records.forEach((id, nhc) -> {
                        logger.info("Record: {} NHC: {}", id, nhc);

                        // Retrieve the patient from Health Data Hub
                        Patient patient = fhir.getPatient(id);

                        // Get the FHIR Id to interact with
                        if (patient == null) {
                            
                        String fhirId = patient.getIdElement()
                            .getIdPart();
                        
                        /* if (fhir.getDocumentReference(fhirId, docName) == false) {

                            logger.info("No pdf document in FHIR"); */
                            
                            if (redcap.exportPDF(id, instrumentInclusion) == false)
                                return;
                        /*    Date dateToday = new Date();

                            logger.info("Creating the Document for patient: " + fhirId);

                            DocumentReference docRef = fhir.createDocumentReference("Patient/" + fhirId, 
                                instrument, dateToday);

                            Bundle bundle = new Bundle();
                            bundle.setType(BundleType.TRANSACTION);

                            bundle.addEntry()
                                .setFullUrl(obs.getId())
                                .setResource(obs)
                                .getRequest()
                                .setMethod(HTTPVerb.POST);

                            fhir.execute(bundle); */
                                        
                            try {
                                logger.info("NHC: {}", nhc);

                                ACK response = client.setORU((String)nhc, patient);
                                if (response != null) {
                                    if ("AR".equalsIgnoreCase(response.getMSA().getMSA1()) )
                                        logger.info("CODE: {} ERROR: {}", response.getMSA().getMSA1(),
                                            response.getERR().getERR3().getCWE2());
                                    else
                                        logger.info("CODE: {} DESCRIPTION: {}",response.getMSA().getMSA1()
                                            + response.getMSA().getMSA2() );
                
                                }
                            } catch (WebServiceException e) {
                                logger.error(e.getMessage());
                            }

                            try {
                                Thread.sleep(timer * 4); // 2 minutes to check again to give FHIR room to update the changes
                            } catch (InterruptedException e) {
                                logger.error(e.getMessage());
                            }
                        }

                        /*} else
                            logger.info("Pdf report {} already stored for patient {}", instrument, fhirId); */
                    });

                Thread.sleep(timer); // Half a minute to check again
            // }

        };
    }
}
