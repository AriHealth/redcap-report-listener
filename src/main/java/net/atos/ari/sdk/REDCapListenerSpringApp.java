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

import java.util.Base64;
import java.util.Map;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Bundle.HTTPVerb;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DocumentReference;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ws.WebServiceException;

import com.google.common.collect.ImmutableMap;

@SpringBootApplication
public class REDCapListenerSpringApp {

    private static final Logger logger = LoggerFactory.getLogger(REDCapListenerSpringApp.class);

    int i = 0;

    /** Timer to wait until the next execution. */
    @Value("${redcap.listener.timer}")
    private Long timer;

    /** REDCap report instruments for the project. */
    @Value("${redcap.project.report.instruments}")
    private String[] instruments;

    /** REDCap report names for the project. */
    @Value("${redcap.project.report.names}")
    private String[] names;

    /** FHIR codes of the Encounter. */
    @Value("${fhir.encounter.codes}")
    private String[] encCodes;

    /** FHIR displays of the Encounter. */
    @Value("${fhir.encounter.displays}")
    private String[] encDisplays;

    /** FHIR codes of the Document. */
    @Value("${fhir.document.codes}")
    private String[] docCodes;

    /** FHIR displays of the Document. */
    @Value("${fhir.document.displays}")
    private String[] docDisplays;

    private final String LOINC = "http://loinc.org";
    private final String SNOMED = "http://snomed.info/sct";
    private final String SNOMED_FIRST_ENCOUNTER = "769681006";

    @Autowired
    private REDCapListenerService redcap;

    @Autowired
    private FHIRListenerService fhir;
    
    String fhirId = null;

    public static void main(String[] args) {
        SpringApplication.run(REDCapListenerSpringApp.class);
    }

    @Bean
    CommandLineRunner lookup(Client client) {
        
        return args -> {

            // Create a new REST client for FHIR
            fhir.configure();

            while (true) {
                for (i=0; i < instruments.length; i++ ) {
                    
                    // Retrieve the records with fisioterapia_basal instrument complete and locked
                    Map<String, Object> records = redcap.export(instruments[i]);
                    if (records != null)
                        records.forEach((id, nhc) -> {
                            logger.info("Record: {} NHC: {}", id, nhc);
    
                            // Retrieve the patient from Health Data Hub
                            Patient patient = fhir.getPatient(id);
    
                            // Get the FHIR Id to interact with
                            if (patient != null) {
                                
                                fhirId = patient.getIdElement()
                                    .getIdPart();
                            
                                if (fhir.getDocumentReference(fhirId, instruments[i]) == false) {
    
                                    logger.info("No pdf document in FHIR"); 
                                    
                                    byte [] encodedBytes = null;
                                    encodedBytes = redcap.exportPDF(id, instruments[i]);
                                    if (encodedBytes != null) {

                                        Bundle bundle = new Bundle();
                                        bundle.setType(BundleType.TRANSACTION);        

                                        byte[] encoded64Bytes = Base64.getEncoder()
                                            .encode(encodedBytes);
                                        
                                        String practId = patient.getGeneralPractitionerFirstRep().getReference();
                                        String orgId = patient.getManagingOrganization().getReference();
                                        
                                        EpisodeOfCare episode = fhir.getFirstEpisode(fhirId);

                                        Encounter encounter = fhir.getEncounter(fhirId, encCodes[i]);
                                        String encounterId = null;
                                        if (encounter != null)
                                            encounterId = encounter.getIdElement().getIdPart();
                                        else {
                                            Coding encCoding = new Coding().setSystem(SNOMED)
                                                .setCode(encCodes[i]).setDisplay(encDisplays[i]);
                                            
                                            encounter = fhir.createEncounter("Patient/" + fhirId, encCoding);
                                            
                                            if (episode != null)
                                                encounter.addEpisodeOfCare(new Reference("EpisodeOfCare/" + 
                                                    episode.getIdElement().getIdPart()));
                                            
                                            encounter.setSubject(new Reference("Patient/" + fhirId));
                                            
                                            encounterId = encounter.getIdElement().getValue();

                                            bundle.addEntry()
                                            .setFullUrl(encounter.getIdElement().getValue())
                                            .setResource(encounter)
                                                .getRequest()
                                                .setMethod(HTTPVerb.POST);
                                        }
                                        
                                        Coding docCoding = new Coding().setSystem(LOINC)
                                            .setCode(docCodes[i]).setDisplay(docDisplays[i]);
                                        
                                        DocumentReference docRef = fhir.createDocumentReference("Patient/" + fhirId, 
                                            instruments[i], names[i], names[i], orgId, practId, "Encounter/" + encounterId,
                                            docCoding, encoded64Bytes);
                                        
                                        bundle.addEntry()
                                        .setFullUrl(docRef.getIdElement().getValue())
                                        .setResource(docRef)
                                            .getRequest()
                                            .setMethod(HTTPVerb.POST);
                                        
                                        fhir.execute(bundle);
                                        
                                        if (docRef != null) {
                                                    
                                            try {
                                                logger.info("NHC: {}", nhc);
                
                                                Encounter firstEncounter = fhir.getEncounter(fhirId, SNOMED_FIRST_ENCOUNTER);
                                                ACK response = client.setORU((String)nhc, patient, names[i], firstEncounter,
                                                    episode, encodedBytes);
                                                if (response != null) {
                                                    if ("AR".equalsIgnoreCase(response.getMSA().getMSA1()) )
                                                        logger.info("CODE: {} ERROR: {}", response.getMSA().getMSA1(),
                                                            response.getERR().getERR3().getCWE2());
                                                    else
                                                        logger.info("CODE: {} DESCRIPTION: {}",response.getMSA().getMSA1(),
                                                            response.getMSA().getMSA2() );
                                
                                                }
                                            } catch (WebServiceException e) {
                                                logger.error(e.getMessage());
                                            }
                                            
                                            // 2 minutes to check again to give FHIR room to update the changes
                                            try {
                                                Thread.sleep(timer * 4);
                                            } catch (InterruptedException e) {
                                                logger.error(e.getMessage());
                                            }
                                        }
                                    }
                                }
                            } else
                                logger.info("Pdf report {} already stored for patient {}", instruments[i], fhirId); 
                        });
                }

                Thread.sleep(timer); // Half a minute to check again
            }

        };
    }
}
